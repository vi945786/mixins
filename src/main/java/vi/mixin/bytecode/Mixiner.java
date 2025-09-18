package vi.mixin.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.transformers.*;
import vi.mixin.api.transformers.accessortype.AccessorClassTransformer;
import vi.mixin.api.transformers.mixintype.MixinClassTransformer;
import vi.mixin.api.transformers.targeteditors.TargetClassEditor;
import vi.mixin.api.transformers.targeteditors.TargetFieldEditor;
import vi.mixin.api.transformers.targeteditors.TargetMethodEditor;

import java.lang.annotation.Annotation;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static vi.mixin.bytecode.Agent.agent;

public class Mixiner {

    private static final Map<String, List<Transformer>> transformers;

    private static final Map<String, ClassNode> originalTargetClassNodes;
    private static final Set<String> usedMixinClasses;

    static {
        if("main".equals(System.getProperty("mixin.stage"))) {
            try {
                Field transformersField = Class.forName(Mixiner.class.getName(), false, ClassLoader.getSystemClassLoader()).getDeclaredField("transformers");
                transformersField.setAccessible(true);
                transformers = (Map<String, List<Transformer>>) transformersField.get(null);

                Field originalTargetClassNodesField = Class.forName(Mixiner.class.getName(), false, ClassLoader.getSystemClassLoader()).getDeclaredField("originalTargetClassNodes");
                originalTargetClassNodesField.setAccessible(true);
                originalTargetClassNodes = (Map<String, ClassNode>) originalTargetClassNodesField.get(null);

                Field usedMixinClassesField = Class.forName(Mixiner.class.getName(), false, ClassLoader.getSystemClassLoader()).getDeclaredField("usedMixinClasses");
                usedMixinClassesField.setAccessible(true);
                usedMixinClasses = (Set<String>) usedMixinClassesField.get(null);
            } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            transformers = new HashMap<>();
            originalTargetClassNodes = new HashMap<>();
            usedMixinClasses = new HashSet<>();
        }
    }

    public static void addMixinTransformer(Transformer transformer, Class<? extends Annotation> annotation) {
        String annotationDesc = "L" + annotation.getName().replace(".", "/") + ";";
        if(transformer instanceof ClassTransformer<?,?,?,?,?> && transformers.getOrDefault(annotationDesc, List.of()).stream().anyMatch(t -> t instanceof ClassTransformer<?,?,?,?,?>)) {
            throw new MixinFormatException(annotation.getName(), "class transformer annotation was already a registered a ClassTransformer");
        }
        transformers.computeIfAbsent(annotationDesc, k -> new ArrayList<>()).add(transformer);
    }

    public static Class<?> getTargetClass(ClassNode mixinClassNode) {
        for (AnnotationNode annotationNode : mixinClassNode.invisibleAnnotations) {
            if(annotationNode.desc.equals(Type.getType(Mixin.class).getDescriptor())) {
                Mixin annotation = TransformerHelper.getAnnotation(annotationNode);
                Class<?> targetClass = annotation.value();
                if(targetClass != void.class) return targetClass;
                targetClass = MixinClassHelper.findClass(annotation.name());
                if(targetClass == null) throw new MixinFormatException(mixinClassNode.name, "invalid @Mixin target");
                return MixinClassHelper.findClass(annotation.name());
            }
        }

        throw new MixinFormatException(mixinClassNode.name, "doesn't have a @Mixin annotation");
    }

    public static void addClasses(List<byte[]> bytecodes) {
        Map<ClassNode, String> outerClassMap = new HashMap<>();
        Map<String, ClassNodeHierarchy> outerClassNodeMap = new HashMap<>();
        Map<ClassNodeHierarchy, List<ClassNodeHierarchy>> classNodeChildrenClassMap = new HashMap<>();
        for(byte[] bytecode : bytecodes) {
            ClassReader mixinClassReader = new ClassReader(bytecode);
            ClassNode mixinClassNode = new ClassNode();
            mixinClassReader.accept(mixinClassNode, 0);
            String outerName = mixinClassNode.innerClasses.stream().filter(inner -> inner.name.equals(mixinClassNode.name)).map(inner -> inner.outerName).findFirst().orElse(null);
            outerClassMap.put(mixinClassNode, outerName);
        }
        bytecodes = null; //allow garbage collection

        outerClassNodeMap.put(null, null);
        classNodeChildrenClassMap.put(null, new ArrayList<>());
        int outerClassMapLen = outerClassMap.size();
        while(!outerClassMap.isEmpty()) {
            for(Map.Entry<ClassNode, String> entry : new HashSet<>(outerClassMap.entrySet())) {
                if(!outerClassNodeMap.containsKey(entry.getValue())) continue;

                List<ClassNodeHierarchy> children = new ArrayList<>();
                ClassNodeHierarchy classNodeHierarchy = new ClassNodeHierarchy(entry.getKey(), outerClassNodeMap.get(entry.getValue()), children);
                classNodeChildrenClassMap.get(outerClassNodeMap.get(entry.getValue())).add(classNodeHierarchy);
                classNodeChildrenClassMap.put(classNodeHierarchy, children);
                outerClassNodeMap.put(entry.getKey().name, classNodeHierarchy);
                outerClassMap.remove(entry.getKey());
            }
            if(outerClassMapLen == outerClassMap.size()) throw new MixinFormatException(outerClassMap.keySet().stream().toList().getFirst().name, "outer class is not a mixin class");
            outerClassMapLen = outerClassMap.size();
        }
         //allow garbage collection
        outerClassNodeMap = null;
        outerClassMap = null;

        classNodeChildrenClassMap.remove(null);

        Map<ClassNode, Class<?>> mixinClassNodesToTargetClass = new HashMap<>();
        Map<ClassNode, MixinResult> mixinClassNodesToMixinResult = new HashMap<>();
        for(ClassNodeHierarchy classNodeHierarchy : classNodeChildrenClassMap.keySet().stream().sorted(Comparator.comparing(a -> a.getClassNode().name)).toList()) {
            ClassNode mixinClassNode = classNodeHierarchy.getClassNode();
            Class<?> targetClass = getTargetClass(mixinClassNode);

            mixinClassNodesToTargetClass.put(mixinClassNode, targetClass);
            mixinClassNodesToMixinResult.put(mixinClassNode, addClass(targetClass, classNodeHierarchy));
        }

        Map<Class<?>, Class<?>> mixinClasses = new HashMap<>();
        for (ClassNodeHierarchy classNodeHierarchy : classNodeChildrenClassMap.keySet()) {
            ClassNode mixinClassNode = classNodeHierarchy.getClassNode();
            Class<?> targetClass = mixinClassNodesToTargetClass.get(mixinClassNode);
            MixinResult mixinResult = mixinClassNodesToMixinResult.get(mixinClassNode);

            try {
                ClassWriter targetWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                ClassWriter mixinWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

                mixinResult.targetClassNode.accept(targetWriter);
                mixinClassNode.accept(mixinWriter);

                new MixinerTransformer(mixinClassNode.name, mixinWriter.toByteArray());
                agent.redefineClasses(new ClassDefinition(targetClass, targetWriter.toByteArray()));

                Class<?> mixinClass = MixinClassHelper.findClass(mixinClassNode.name);
                agent.redefineModule(targetClass.getModule(), Stream.of(mixinClass.getModule(), Mixiner.class.getModule()).collect(Collectors.toSet()), Map.of(targetClass.getPackageName(), Set.of(mixinClass.getModule())), new HashMap<>(), new HashSet<>(), new HashMap<>());

                mixinClasses.put(mixinClass, targetClass);

                String methodName = mixinResult.setUpMethodName;
                if(methodName == null) continue;
                Method method = mixinClass.getMethod(methodName);
                if((method.getModifiers() & Opcodes.ACC_STATIC) == 0) throw new MixinFormatException(mixinClassNode.name, "the \"set up\" method must be static");
                method.setAccessible(true);
                method.invoke(null);

            } catch (ClassNotFoundException | UnmodifiableClassException | InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                throw new MixinFormatException(mixinClassNode.name, "added \"set up\" method not found. make sure it takes no parameters");
            }
        }
         //allow garbage collection
        mixinClassNodesToTargetClass = null;
        mixinClassNodesToMixinResult = null;

        for(Map.Entry<Class<?>, Class<?>> mixinClass : mixinClasses.entrySet()) {
            for(Class<?> inner : mixinClass.getKey().getDeclaredClasses()) {
                if(!mixinClasses.containsKey(inner)) throw new MixinFormatException(inner.getName(), "inner class of mixin is not a mixin class");
            }

            if(!mixinClass.getKey().isMemberClass()) continue;

            Class<?> outer = mixinClass.getKey().getEnclosingClass();
            Class<?> outerTarget = mixinClasses.get(outer);
            if(Arrays.stream(outerTarget.getDeclaredClasses()).noneMatch(c -> c == mixinClass.getValue())) {
                throw new MixinFormatException(mixinClass.getKey().getName().replace(".", "/"), "inner target " + mixinClass.getValue() + " is not an inner class of target " + outerTarget);
            }
        }
    }

    private static MixinResult addClass(Class<?> targetClass, ClassNodeHierarchy mixinClassNodeHierarchy) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.getClassNode();
        if(usedMixinClasses.contains(mixinClassNode.name)) throw new IllegalArgumentException("Mixin class " + mixinClassNode.name + " used twice");
        usedMixinClasses.add(mixinClassNode.name);

        return mixin(targetClass, mixinClassNodeHierarchy);
    }

    private static MixinResult mixin(Class<?> targetClass, ClassNodeHierarchy mixinClassNodeHierarchy) {
        byte[] targetBytecode = Agent.getBytecode(targetClass);
        ClassReader targetClassReader = new ClassReader(targetBytecode);
        ClassNode modifyTargetClassNode = new ClassNode();
        targetClassReader.accept(modifyTargetClassNode, 0);
        if(!originalTargetClassNodes.containsKey(targetClass.getName())) {
            ClassNode clone = new ClassNode();
            modifyTargetClassNode.accept(clone);
            originalTargetClassNodes.put(targetClass.getName(), clone);
        }

        ClassNode originalTargetClassNode = originalTargetClassNodes.get(targetClass.getName());
        ClassNode modifyMixinClassNode = mixinClassNodeHierarchy.getClassNode();
        ClassNode originalMixinClassNode = new ClassNode();
        modifyMixinClassNode.accept(originalMixinClassNode);

        Annotation mixinClassAnnotation = null;
        ClassTransformer classTransformer = null;
        for (AnnotationNode annotationNode : originalMixinClassNode.invisibleAnnotations) {
            List<Transformer> transformers = Mixiner.transformers.getOrDefault(annotationNode.desc, List.of());
            transformers = transformers.stream().filter(t -> t instanceof ClassTransformer<?,?,?,?,?>).toList();
            if(transformers.isEmpty()) continue;
            if(!(transformers.getFirst() instanceof ClassTransformer cTransformer)) throw new MixinFormatException(originalMixinClassNode.name, "class annotation transformer is not an instance of ClassTransformer");
            if(classTransformer != null) throw new MixinFormatException(originalMixinClassNode.name, "mixin class has 2 class transformers");
            classTransformer = cTransformer;
            mixinClassAnnotation = TransformerHelper.getAnnotation(annotationNode);
        }
        if(classTransformer == null) {
            if((originalMixinClassNode.access & Opcodes.ACC_INTERFACE) == 0) classTransformer = new MixinClassTransformer();
            else classTransformer = new AccessorClassTransformer();
        }
        Class<? extends MethodTransformer> methodTransformerType = classTransformer.getMethodTransformerType();
        Class<? extends FieldTransformer> fieldTransformerType = classTransformer.getFieldTransformerType();

        Map<MethodNode, MethodEditor> methodNodeEditorMap = new HashMap<>();
        Map<FieldNode, FieldEditor> fieldNodeEditorMap = new HashMap<>();

        for (MethodNode methodNode : modifyMixinClassNode.methods) {
            if(methodNode.invisibleAnnotations == null) {

                methodNodeEditorMap.put(methodNode, classTransformer.create(methodNode, new TargetMethodEditor[0]));
                continue;
            }
            for (AnnotationNode annotationNode : methodNode.invisibleAnnotations) {
                List<Transformer> transformers = Mixiner.transformers.getOrDefault(annotationNode.desc, List.of());
                transformers = transformers.stream().filter(t -> methodTransformerType.isAssignableFrom(t.getClass())).toList();
                if(transformers.isEmpty() || !(transformers.getFirst() instanceof MethodTransformer methodTransformer)) continue;
                if(transformers.size() != 1) throw new MixinFormatException(annotationNode.desc, "has more than one transformer registered for " + classTransformer.getClass().getName());

                Annotation annotation = TransformerHelper.getAnnotation(annotationNode);
                ClassNode mixinClassNodeClone = new ClassNode();
                originalMixinClassNode.accept(mixinClassNodeClone);
                ClassNode targetClassNodeClone = new ClassNode();
                originalTargetClassNode.accept(targetClassNodeClone);
                switch (methodTransformer.getTargetMethodType()) {
                    case METHOD -> {
                        MethodEditor editor = classTransformer.create(methodNode, getTargetMethodEditors(methodTransformer, methodNode, originalTargetClassNode, modifyTargetClassNode, annotation).toArray(TargetMethodEditor[]::new));
                        methodNodeEditorMap.put(methodNode, editor);
                        methodTransformer.transform(editor, annotation, mixinClassNodeClone, targetClassNodeClone);
                    }
                    case FIELD -> {
                        FieldEditor editor = classTransformer.create(methodNode, getFieldTargetEditors(methodTransformer, methodNode, originalTargetClassNode, modifyTargetClassNode, annotation).toArray(TargetFieldEditor[]::new));
                        fieldNodeEditorMap.put(null, editor);
                        methodTransformer.transform(editor, annotation, mixinClassNodeClone, targetClassNodeClone);
                    }
                }
            }
        }

        for (FieldNode fieldNode : modifyMixinClassNode.fields) {
            if(fieldNode.invisibleAnnotations == null) {
                fieldNodeEditorMap.put(fieldNode, classTransformer.create(fieldNode, new TargetFieldEditor[0]));
                continue;
            }
            for (AnnotationNode annotationNode : fieldNode.invisibleAnnotations) {
                List<Transformer> transformers = Mixiner.transformers.getOrDefault(annotationNode.desc, List.of());
                transformers = transformers.stream().filter(t -> fieldTransformerType.isAssignableFrom(t.getClass())).toList();
                if(transformers.isEmpty() || !(transformers.getFirst() instanceof FieldTransformer fieldTransformer)) continue;
                if(transformers.size() != 1) throw new MixinFormatException(annotationNode.desc, "has more than one transformer registered for " + classTransformer.getClass().getName());

                Annotation annotation = TransformerHelper.getAnnotation(annotationNode);
                ClassNode mixinClassNodeClone = new ClassNode();
                originalMixinClassNode.accept(mixinClassNodeClone);
                ClassNode targetClassNodeClone = new ClassNode();
                originalTargetClassNode.accept(targetClassNodeClone);
                switch (fieldTransformer.getFieldTargetType()) {
                    case METHOD -> {
                        MethodEditor editor = classTransformer.create(fieldNode, getTargetMethodEditors(fieldTransformer, fieldNode, originalTargetClassNode, modifyTargetClassNode, annotation).toArray(TargetMethodEditor[]::new));
                        methodNodeEditorMap.put(null, editor);
                        fieldTransformer.transform(editor, annotation, mixinClassNodeClone, targetClassNodeClone);
                    }
                    case FIELD -> {
                        FieldEditor editor = classTransformer.create(fieldNode, getFieldTargetEditors(fieldTransformer, fieldNode, originalTargetClassNode, modifyTargetClassNode, annotation).toArray(TargetFieldEditor[]::new));
                        fieldNodeEditorMap.put(fieldNode, editor);
                        fieldTransformer.transform(editor, annotation, mixinClassNodeClone, targetClassNodeClone);
                    }
                }
            }
        }

        return new MixinResult(modifyTargetClassNode, classTransformer.transform(mixinClassNodeHierarchy, methodNodeEditorMap, fieldNodeEditorMap, mixinClassAnnotation, new TargetClassEditor(modifyTargetClassNode, originalTargetClassNode, targetClass)));
    }

    private static List<TargetMethodEditor> getTargetMethodEditors(InnerElementTransformer innerElementTransformer, MethodNode methodNode, ClassNode originalTargetClassNode, ClassNode modifyTargetClassNode, Annotation annotation) {
        Map<String, MethodNode> modifyTargetMethodNodes = new HashMap<>();
        modifyTargetClassNode.methods.forEach(m -> modifyTargetMethodNodes.put(m.name + m.desc, m));

        return originalTargetClassNode.methods.stream().filter(m -> {
            ClassNode cloneTarget = new ClassNode();
            m.accept(cloneTarget);

            ClassNode cloneMixin = new ClassNode();
            methodNode.accept(cloneMixin);

            return innerElementTransformer.isMethodTarget(cloneMixin.methods.getFirst(), cloneTarget.methods.getFirst(), annotation);
        }).map(m -> new TargetMethodEditor(modifyTargetMethodNodes.get(m.name + m.desc), m)).toList();
    }

    private static List<TargetFieldEditor> getFieldTargetEditors(InnerElementTransformer innerElementTransformer, FieldNode fieldNode, ClassNode targetClassNode, ClassNode modifyTargetClassNode, Annotation annotation) {
        Map<String, FieldNode> modifyFieldNodes = new HashMap<>();
        modifyTargetClassNode.fields.forEach(f -> modifyFieldNodes.put(f.name, f));

        return targetClassNode.fields.stream().filter(m -> {
            ClassNode clone = new ClassNode();
            m.accept(clone);

            ClassNode cloneMixin = new ClassNode();
            fieldNode.accept(cloneMixin);

            return innerElementTransformer.isFieldTarget(cloneMixin.fields.getFirst(), clone.fields.getFirst(), annotation);
        }).map(f -> new TargetFieldEditor(modifyFieldNodes.get(f.name), f)).toList();
    }

        private static List<TargetMethodEditor> getTargetMethodEditors(InnerElementTransformer innerElementTransformer, FieldNode fieldNode, ClassNode originalTargetClassNode, ClassNode modifyTargetClassNode, Annotation annotation) {
        Map<String, MethodNode> modifyTargetMethodNodes = new HashMap<>();
        modifyTargetClassNode.methods.forEach(m -> modifyTargetMethodNodes.put(m.name + m.desc, m));

        return originalTargetClassNode.methods.stream().filter(m -> {
            ClassNode cloneTarget = new ClassNode();
            m.accept(cloneTarget);

            ClassNode cloneMixin = new ClassNode();
            fieldNode.accept(cloneMixin);

            return innerElementTransformer.isMethodTarget(cloneMixin.fields.getFirst(), cloneTarget.methods.getFirst(), annotation);
        }).map(m -> new TargetMethodEditor(modifyTargetMethodNodes.get(m.name + m.desc), m)).toList();
    }

    private static List<TargetFieldEditor> getFieldTargetEditors(InnerElementTransformer innerElementTransformer, MethodNode methodNode, ClassNode targetClassNode, ClassNode modifyTargetClassNode, Annotation annotation) {
        Map<String, FieldNode> modifyFieldNodes = new HashMap<>();
        modifyTargetClassNode.fields.forEach(f -> modifyFieldNodes.put(f.name, f));

        return targetClassNode.fields.stream().filter(m -> {
            ClassNode clone = new ClassNode();
            m.accept(clone);

            ClassNode cloneMixin = new ClassNode();
            methodNode.accept(cloneMixin);

            return innerElementTransformer.isFieldTarget(cloneMixin.methods.getFirst(), clone.fields.getFirst(), annotation);
        }).map(f -> new TargetFieldEditor(modifyFieldNodes.get(f.name), f)).toList();
    }

    private record MixinResult(ClassNode targetClassNode, String setUpMethodName) {}

    private record MixinerTransformer(String name, byte[] bytecode) implements ClassFileTransformer {

        private MixinerTransformer(String name, byte[] bytecode) {
            this.name = name;
            this.bytecode = bytecode;
            agent.addTransformer(this);
        }

        public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (className.equals(name)) {
                agent.removeTransformer(this);
                return bytecode;
            }
            return null;
        }
    }
}
