package vi.mixin.bytecode;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.classtypes.ClassNodeHierarchy;
import vi.mixin.api.classtypes.Editors;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.classtypes.accessortype.AccessorMixinClassType;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.classtypes.mixintype.MixinMixinClassType;
import vi.mixin.api.util.TransformerHelper;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetClassEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

import java.lang.annotation.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static vi.mixin.bytecode.Agent.agent;

public class Mixiner {

    private final Map<String, MixinClassType<?, ?, ?, ?, ?>> mixinClassTypes = new HashMap<>();
    private final Map<String, List<BuiltTransformer>> transformers = new HashMap<>();

    private final Map<String, ClassNode> originalTargetClassNodes = new HashMap<>();
    private final Set<String> usedMixinClasses = new HashSet<>();

    Mixiner() {}

    public static Class<?> getTargetClass(ClassNode mixinClassNode) {
        for (AnnotationNode annotationNode : mixinClassNode.invisibleAnnotations) {
            if(annotationNode.desc.equals(Type.getType(Mixin.class).getDescriptor())) {
                Mixin annotation = TransformerHelper.getAnnotation(annotationNode);
                Class<?> targetClass = annotation.value();
                if(targetClass == void.class) {
                    targetClass = MixinClassHelper.findClass(annotation.name());
                    if (targetClass == null) throw new MixinFormatException(mixinClassNode.name, "@Mixin target not found");
                } else if (!annotation.name().isEmpty()) {
                    throw new MixinFormatException(mixinClassNode.name, "@Mixin \"value\" and \"name\" set");
                }
                if (targetClass == Object.class) throw new MixinFormatException(mixinClassNode.name, "@Mixin target cannot be Object");
                return targetClass;
            }
        }

        throw new MixinFormatException(mixinClassNode.name, "doesn't have a @Mixin annotation");
    }

    public void addMixinClassType(MixinClassType<?, ?, ?, ?, ?> mixinClassType) {
        for(java.lang.reflect.Type type : mixinClassType.getClass().getGenericInterfaces()) {
            if(!type.getTypeName().split("<")[0].equals(MixinClassType.class.getName())) continue;
            if(!(type instanceof ParameterizedType pt)) throw new MixinFormatException(mixinClassType.getClass().getName(), "mixin class type must pass generic parameters to the to MixinClassType interface");

            String annotationDesc = "L" + pt.getActualTypeArguments()[0].getTypeName().split("<")[0].replace(".", "/") + ";";

            if(mixinClassTypes.containsKey(annotationDesc)) throw new IllegalArgumentException(annotationDesc + " is already registered for a mixin class type annotation");
            mixinClassTypes.put(annotationDesc, mixinClassType);
        }
    }

    public void addBuiltTransformer(BuiltTransformer transformer) {
        String annotationDesc = "L" + transformer.getAnnotation().getName().replace(".", "/") + ";";

        if(transformers.getOrDefault(annotationDesc, List.of()).stream().anyMatch(t -> t.getMixinClassType().getName().equals(transformer.getMixinClassType().getName()) && t.isAnnotatedMethod() == transformer.isAnnotatedMethod()))
            throw new IllegalArgumentException(transformer.getAnnotation().getName() + " is already registered for mixin type " + transformer.getMixinClassType().getName());
        transformers.computeIfAbsent(annotationDesc, (k) -> new ArrayList<>()).add(transformer);
    }

    public void addClasses(List<byte[]> mixins, List<byte[]> anonymousInners) {
        Map<ClassNode, String> outerClassMap = new HashMap<>();
        Map<String, ClassNodeHierarchy> outerClassNodeMap = new HashMap<>();
        Map<ClassNodeHierarchy, List<ClassNodeHierarchy>> classNodeChildrenClassMap = new HashMap<>();
        for(byte[] bytecode : mixins) {
            ClassReader mixinClassReader = new ClassReader(bytecode);
            ClassNode mixinClassNode = new ClassNode();
            mixinClassReader.accept(mixinClassNode, 0);
            String outerName = mixinClassNode.innerClasses.stream().filter(inner -> inner.name.equals(mixinClassNode.name)).map(inner -> inner.outerName).findFirst().orElse(null);
            outerClassMap.put(mixinClassNode, outerName);
        }
        for(byte[] bytecode : anonymousInners) {
            ClassReader mixinClassReader = new ClassReader(bytecode);
            ClassNode mixinClassNode = new ClassNode();
            mixinClassReader.accept(mixinClassNode, 0);
            String outerName = mixinClassNode.outerClass;
            outerClassMap.put(mixinClassNode, outerName);
            mixinClassNode.invisibleAnnotations = new ArrayList<>();

            AnnotationNode mixin = new AnnotationNode(Type.getDescriptor(Mixin.class));
            mixin.values = new ArrayList<>(List.of("value", Type.getType("L" + mixinClassNode.name + ";")));
            mixinClassNode.invisibleAnnotations.add(mixin);
            mixinClassNode.invisibleAnnotations.add(new AnnotationNode("Lvi/mixin/api/classtypes/anonymoustype/AnonymousMixinClassType$Anonymous;"));
        }
        mixins.clear();
        anonymousInners.clear();

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
        outerClassNodeMap.clear();
        outerClassMap.clear();

        classNodeChildrenClassMap.remove(null);

        Map<ClassNode, Class<?>> mixinClassNodesToTargetClass = new HashMap<>();
        Map<ClassNode, MixinResult> mixinClassNodesToMixinResult = new HashMap<>();
        for(ClassNodeHierarchy classNodeHierarchy : classNodeChildrenClassMap.keySet().stream().sorted(Comparator.comparing(a -> a.classNode().name)).toList()) {
            ClassNode mixinClassNode = classNodeHierarchy.classNode();
            Class<?> targetClass = getTargetClass(mixinClassNode);

            mixinClassNodesToTargetClass.put(mixinClassNode, targetClass);
            mixinClassNodesToMixinResult.put(mixinClassNode, addClass(targetClass, classNodeHierarchy));
        }

        Map<Class<?>, Class<?>> mixinClasses = new HashMap<>();
        for (ClassNodeHierarchy classNodeHierarchy : classNodeChildrenClassMap.keySet()) {
            ClassNode mixinClassNode = classNodeHierarchy.classNode();

            Class<?> targetClass = mixinClassNodesToTargetClass.get(mixinClassNode);
            MixinResult mixinResult = mixinClassNodesToMixinResult.get(mixinClassNode);

            try {
                ClassWriter targetWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                ClassWriter mixinWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

                mixinResult.targetClassNode.accept(targetWriter);
                mixinClassNode.accept(mixinWriter);

                new MixinerTransformer(mixinClassNode.name, mixinWriter.toByteArray());
                if(mixinResult.redefineTargetFirst) agent.redefineClasses(new ClassDefinition(targetClass, targetWriter.toByteArray()));
                Class<?> mixinClass = MixinClassHelper.findClass(mixinClassNode.name);
                assert mixinClass != null;
                agent.redefineModule(targetClass.getModule(), Stream.of(mixinClass.getModule(), Mixiner.class.getModule()).collect(Collectors.toSet()), Map.of(targetClass.getPackageName(), Set.of(mixinClass.getModule())), new HashMap<>(), new HashSet<>(), new HashMap<>());
                if(!mixinResult.redefineTargetFirst)agent.redefineClasses(new ClassDefinition(targetClass, targetWriter.toByteArray()));


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
        classNodeChildrenClassMap.clear();
        mixinClassNodesToTargetClass.clear();
        mixinClassNodesToMixinResult.clear();

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

    private MixinResult addClass(Class<?> targetClass, ClassNodeHierarchy mixinClassNodeHierarchy) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.classNode();
        if(usedMixinClasses.contains(mixinClassNode.name)) throw new IllegalArgumentException("Mixin class " + mixinClassNode.name + " used twice");
        usedMixinClasses.add(mixinClassNode.name);

        return mixin(targetClass, mixinClassNodeHierarchy);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private MixinResult mixin(Class<?> targetClass, ClassNodeHierarchy mixinClassNodeHierarchy) {
        byte[] targetBytecode = Agent.getBytecode(targetClass);
        ClassReader targetClassReader = new ClassReader(targetBytecode);
        ClassNode modifyTargetClassNode = new ClassNode();
        targetClassReader.accept(modifyTargetClassNode, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        if(!originalTargetClassNodes.containsKey(targetClass.getName())) {
            ClassNode clone = new ClassNode();
            modifyTargetClassNode.accept(clone);
            originalTargetClassNodes.put(targetClass.getName(), clone);
        }

        ClassNode originalTargetClassNode = originalTargetClassNodes.get(targetClass.getName());
        ClassNode modifyMixinClassNode = mixinClassNodeHierarchy.classNode();
        ClassNode originalMixinClassNode = new ClassNode();
        modifyMixinClassNode.accept(originalMixinClassNode);

        Annotation mixinClassAnnotation = null;
        MixinClassType mixinClassType = null;
        String mixinClassTypeName;
        for (AnnotationNode annotationNode : originalMixinClassNode.invisibleAnnotations) {
            mixinClassType = mixinClassTypes.get(annotationNode.desc);
            if(mixinClassType == null) continue;
            mixinClassAnnotation = TransformerHelper.getAnnotation(annotationNode);
            break;
        }
        if(mixinClassType == null) {
            if((originalMixinClassNode.access & Opcodes.ACC_INTERFACE) == 0) mixinClassType = new MixinMixinClassType();
            else mixinClassType = new AccessorMixinClassType();
        }
        mixinClassTypeName = mixinClassType.getClass().getName();

        Map<MethodNode, AnnotatedMethodEditor> mixinMethodEditors = new HashMap<>();
        Map<FieldNode, AnnotatedFieldEditor> mixinFieldEditors = new HashMap<>();
        Map<MixinClassTargetMethodEditor, TargetMethodEditor> targetMethodEditors = new HashMap<>();
        Map<MixinClassTargetFieldEditor, TargetFieldEditor> targetFieldEditors = new HashMap<>();

        for (MethodNode methodNode : modifyMixinClassNode.methods) {
            if (methodNode.invisibleAnnotations == null) {
                mixinMethodEditors.put(methodNode, mixinClassType.create(methodNode, null));
                continue;
            }
            for (AnnotationNode annotationNode : methodNode.invisibleAnnotations) {
                List<BuiltTransformer> transformers = this.transformers.getOrDefault(annotationNode.desc, List.of());
                BuiltTransformer builtTransformer = transformers.stream().filter(t -> t.getMixinClassType().getName().equals(mixinClassTypeName) && t.isAnnotatedMethod()).findAny().orElse(null);
                if (builtTransformer == null) continue;

                String name = originalMixinClassNode.name + "." + methodNode.name + methodNode.desc;

                Annotation annotation = TransformerHelper.getAnnotation(annotationNode);
                ClassNode mixinClassNodeClone = new ClassNode();
                originalMixinClassNode.accept(mixinClassNodeClone);
                ClassNode targetClassNodeClone = new ClassNode();
                originalTargetClassNode.accept(targetClassNodeClone);
                if (builtTransformer.isTargetMethod()) {
                    MixinClassTargetMethodEditor target = getTargetMethodEditor(builtTransformer, name, methodNode, originalTargetClassNode, modifyTargetClassNode, annotation);
                    mixinMethodEditors.put(methodNode, mixinClassType.create(methodNode, target));
                    TargetMethodEditor targetMethodEditor = mixinClassType.create(target, mixinMethodEditors.get(methodNode));
                    targetMethodEditors.put(target, targetMethodEditor);
                    builtTransformer.transform(mixinMethodEditors.get(methodNode), targetMethodEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
                } else {
                    MixinClassTargetFieldEditor target = getFieldTargetEditor(builtTransformer, name, methodNode, originalTargetClassNode, modifyTargetClassNode, annotation);
                    mixinMethodEditors.put(methodNode, mixinClassType.create(methodNode, target));
                    TargetFieldEditor targetFieldEditor = mixinClassType.create(target, mixinMethodEditors.get(methodNode));
                    targetFieldEditors.put(target, targetFieldEditor);
                    builtTransformer.transform(mixinMethodEditors.get(methodNode), targetFieldEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
                }
            }
        }

        for (FieldNode fieldNode : modifyMixinClassNode.fields) {
            if (fieldNode.invisibleAnnotations == null) {
                mixinFieldEditors.put(fieldNode, mixinClassType.create(fieldNode, null));
                continue;
            }
            for (AnnotationNode annotationNode : fieldNode.invisibleAnnotations) {
                List<BuiltTransformer> transformers = this.transformers.getOrDefault(annotationNode.desc, List.of());
                BuiltTransformer builtTransformer = transformers.stream().filter(t -> t.getMixinClassType().getName().equals(mixinClassTypeName) && !t.isAnnotatedMethod()).findAny().orElse(null);
                if (builtTransformer == null) continue;

                String name = originalMixinClassNode.name+ "." + fieldNode.name;

                Annotation annotation = TransformerHelper.getAnnotation(annotationNode);
                ClassNode mixinClassNodeClone = new ClassNode();
                originalMixinClassNode.accept(mixinClassNodeClone);
                ClassNode targetClassNodeClone = new ClassNode();
                originalTargetClassNode.accept(targetClassNodeClone);
                if (builtTransformer.isTargetMethod()) {
                    MixinClassTargetMethodEditor target = getTargetMethodEditor(builtTransformer, name, fieldNode, originalTargetClassNode, modifyTargetClassNode, annotation);
                    mixinFieldEditors.put(fieldNode, mixinClassType.create(fieldNode, target));
                    TargetMethodEditor targetMethodEditor = mixinClassType.create(target, mixinFieldEditors.get(fieldNode));
                    targetMethodEditors.put(target, targetMethodEditor);
                    builtTransformer.transform(mixinFieldEditors.get(fieldNode), targetMethodEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
                } else {
                    MixinClassTargetFieldEditor target = getFieldTargetEditor(builtTransformer, name, fieldNode, originalTargetClassNode, modifyTargetClassNode, annotation);
                    mixinFieldEditors.put(fieldNode, mixinClassType.create(fieldNode, target));
                    TargetFieldEditor targetFieldEditor = mixinClassType.create(target, mixinFieldEditors.get(fieldNode));
                    targetFieldEditors.put(target, targetFieldEditor);
                    builtTransformer.transform(mixinFieldEditors.get(fieldNode), targetFieldEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
                }
            }
        }

        return new MixinResult(mixinClassType.redefineTargetFirst(), modifyTargetClassNode, mixinClassType.transform(mixinClassNodeHierarchy, new Editors<>(mixinMethodEditors, mixinFieldEditors, targetMethodEditors, targetFieldEditors), mixinClassAnnotation, new MixinClassTargetClassEditor(modifyTargetClassNode, originalTargetClassNode, targetClass)));
    }

    private static <A extends Annotation> MixinClassTargetMethodEditor getTargetMethodEditor(BuiltTransformer builtTransformer, String name, MethodNode methodNode, ClassNode originalTargetClassNode, ClassNode modifyTargetClassNode, A annotation) {
        Map<String, MethodNode> modifyTargetMethodNodes = new HashMap<>();
        modifyTargetClassNode.methods.forEach(m -> modifyTargetMethodNodes.put(m.name + m.desc, m));

        for(MethodNode m : originalTargetClassNode.methods) {
            ClassNode cloneTarget = new ClassNode();
            m.accept(cloneTarget);

            ClassNode cloneMixin = new ClassNode();
            methodNode.accept(cloneMixin);

            if(builtTransformer.isTarget(cloneMixin.methods.getFirst(), cloneTarget.methods.getFirst(), annotation))
                return new MixinClassTargetMethodEditor(originalTargetClassNode.name, modifyTargetMethodNodes.get(m.name + m.desc), m);
        }
        throw new MixinFormatException(name, "doesn't have a target");
    }

    private static <A extends Annotation> MixinClassTargetFieldEditor getFieldTargetEditor(BuiltTransformer builtTransformer, String name, FieldNode fieldNode, ClassNode originalTargetClassNode, ClassNode modifyTargetClassNode, A annotation) {
        Map<String, FieldNode> modifyFieldNodes = new HashMap<>();
        modifyTargetClassNode.fields.forEach(f -> modifyFieldNodes.put(f.name, f));

        for(FieldNode f : originalTargetClassNode.fields) {
            ClassNode clone = new ClassNode();
            f.accept(clone);

            ClassNode cloneMixin = new ClassNode();
            fieldNode.accept(cloneMixin);

            if(builtTransformer.isTarget(cloneMixin.fields.getFirst(), clone.fields.getFirst(), annotation))
                return new MixinClassTargetFieldEditor(modifyFieldNodes.get(f.name), f);
        }
        throw new MixinFormatException(name, "doesn't have a target");
    }

    private static <A extends Annotation> MixinClassTargetMethodEditor getTargetMethodEditor(BuiltTransformer builtTransformer, String name, FieldNode fieldNode, ClassNode originalTargetClassNode, ClassNode modifyTargetClassNode, A annotation) {
        Map<String, MethodNode> modifyTargetMethodNodes = new HashMap<>();
        modifyTargetClassNode.methods.forEach(m -> modifyTargetMethodNodes.put(m.name + m.desc, m));

        for(MethodNode m : originalTargetClassNode.methods) {
            ClassNode cloneTarget = new ClassNode();
            m.accept(cloneTarget);

            ClassNode cloneMixin = new ClassNode();
            fieldNode.accept(cloneMixin);

            if(builtTransformer.isTarget(cloneMixin.fields.getFirst(), cloneTarget.methods.getFirst(), annotation))
                return new MixinClassTargetMethodEditor(originalTargetClassNode.name, modifyTargetMethodNodes.get(m.name + m.desc), m);
        }
        throw new MixinFormatException(name, "doesn't have a target");
    }

    private static <A extends Annotation> MixinClassTargetFieldEditor getFieldTargetEditor(BuiltTransformer builtTransformer, String name, MethodNode methodNode, ClassNode originalTargetClassNode, ClassNode modifyTargetClassNode, A annotation) {
        Map<String, FieldNode> modifyFieldNodes = new HashMap<>();
        modifyTargetClassNode.fields.forEach(f -> modifyFieldNodes.put(f.name, f));

        for(FieldNode f : originalTargetClassNode.fields) {
            ClassNode clone = new ClassNode();
            f.accept(clone);

            ClassNode cloneMixin = new ClassNode();
            methodNode.accept(cloneMixin);

            if (builtTransformer.isTarget(cloneMixin.methods.getFirst(), clone.fields.getFirst(), annotation))
                return new MixinClassTargetFieldEditor(modifyFieldNodes.get(f.name), f);
        }
        throw new MixinFormatException(name, "doesn't have a target");
    }

    private record MixinResult(boolean redefineTargetFirst, ClassNode targetClassNode, String setUpMethodName) {}

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
