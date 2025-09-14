package vi.mixin.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.annotations.fields.Mutable;
import vi.mixin.api.editors.AnnotationEditor;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.*;
import vi.mixin.bytecode.Transformers.*;
import vi.mixin.bytecode.Transformers.MixinTransformer;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static vi.mixin.bytecode.Agent.agent;

public class Mixiner {

    private static final Map<String, ClassTransformer<?>> classTransformers = new HashMap<>();
    private static final Map<String, FieldTransformer<?>> fieldTransformers = new HashMap<>();
    private static final Map<String, MethodTransformer<?>> methodTransformers = new HashMap<>();
    private static final Dummy dummy = new Dummy();

    private static final Map<String, ClassEditor> classEditors;
    private static final Map<ClassEditor, ClassNode> classEditorNodes;
    private static final Set<String> usedMixinClasses;

    static {
        addMixinTransformer(new ExtendsTransformer(), Extends.class);
        addMixinTransformer(new GetterTransformer(), Getter.class);
        addMixinTransformer(new InjectTransformer(), Inject.class);
        addMixinTransformer(new InvokerTransformer(), Invoker.class);
        addMixinTransformer(new MixinTransformer(), Mixin.class);
        addMixinTransformer(new MutableTransformer(), Mutable.class);
        addMixinTransformer(new NewTransformer(), New.class);
        addMixinTransformer(new OverridableTransformer(), Overridable.class);
        addMixinTransformer(new SetterTransformer(), Setter.class);
        addMixinTransformer(new ShadowTransformer(), Shadow.class);

        if("true".equals(System.getProperty("mixin.stage.main"))) {
            try {
                Field classEditorsField = Class.forName(Mixiner.class.getName(), false, ClassLoader.getSystemClassLoader()).getDeclaredField("classEditors");
                classEditorsField.setAccessible(true);
                classEditors = (Map<String, ClassEditor>) classEditorsField.get(null);

                Field classEditorNodesField = Class.forName(Mixiner.class.getName(), false, ClassLoader.getSystemClassLoader()).getDeclaredField("classEditorNodes");
                classEditorNodesField.setAccessible(true);
                classEditorNodes = (Map<ClassEditor, ClassNode>) classEditorNodesField.get(null);

                Field usedMixinClassesField = Class.forName(Mixiner.class.getName(), false, ClassLoader.getSystemClassLoader()).getDeclaredField("usedMixinClasses");
                usedMixinClassesField.setAccessible(true);
                usedMixinClasses = (Set<String>) usedMixinClassesField.get(null);

            } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            classEditors = new HashMap<>();
            classEditorNodes = new HashMap<>();
            usedMixinClasses = new HashSet<>();
        }
    }

    public static void addMixinTransformer(Transformer transformer, Class<? extends Annotation> annotation) {
        String annotationDesc = "L" + annotation.getName().replace(".", "/") + ";";
        if(transformer instanceof ClassTransformer<?> classTransformer) {
            if(classTransformers.containsKey(annotationDesc))
                throw new IllegalArgumentException(annotation.getName() + " is already a registered annotation for a ClassTransformer");
            classTransformers.put(annotationDesc, classTransformer);
        }
        if(transformer instanceof FieldTransformer<?> fieldTransformer) {
            if(fieldTransformers.containsKey(annotationDesc))
                throw new IllegalArgumentException(annotation.getName() + " is already a registered annotation for a FieldTransformer");
            fieldTransformers.put(annotationDesc, fieldTransformer);
        }
        if(transformer instanceof MethodTransformer<?> methodTransformer) {
            if(methodTransformers.containsKey(annotationDesc))
                throw new IllegalArgumentException(annotation.getName() + " is already a registered annotation for a MethodTransformer");
            methodTransformers.put(annotationDesc, methodTransformer);
        }
    }

    public static Class<?> getTargetClass(ClassEditor mixinClassEditor) {
        for (AnnotationEditor annotationEditor : mixinClassEditor.getAnnotationEditors()) {
            if(annotationEditor.getDesc().equals(Type.getType(Mixin.class).getDescriptor())) {
                Mixin annotation = annotationEditor.getAnnotation();
                Class<?> targetClass = annotation.value();
                if(targetClass != void.class) return targetClass;
                targetClass = MixinClassHelper.findClass(annotation.name());
                if(targetClass == null) throw new MixinFormatException(mixinClassEditor.getName(), "invalid @Mixin target");
                return MixinClassHelper.findClass(annotation.name());
            }
        }

        throw new MixinFormatException(mixinClassEditor.getName(), "doesn't have a @Mixin annotation");
    }

    public static void addClasses(List<byte[]> bytecodes) {
        Map<ClassNode, String> outerClassMap = new HashMap<>();
        Map<String, ClassEditor> outerClassNodeMap = new HashMap<>();
        Map<ClassEditor, List<ClassEditor>> classEditorInnerClassMap = new HashMap<>();
        Map<ClassEditor, ClassNode> classEditorClassNodeMap = new HashMap<>();
        bytecodes.forEach(bytecode -> {
            ClassReader mixinClassReader = new ClassReader(bytecode);
            ClassNode mixinClassNode = new ClassNode();
            mixinClassReader.accept(mixinClassNode, 0);
            String outerName = mixinClassNode.innerClasses.stream().filter(inner -> inner.name.equals(mixinClassNode.name)).map(inner -> inner.outerName).findFirst().orElse(null);
            outerClassMap.put(mixinClassNode, outerName);
        });

        outerClassNodeMap.put(null, null);
        classEditorInnerClassMap.put(null, new ArrayList<>());
        int outerClassMapLen = outerClassMap.size();
        while(!outerClassMap.isEmpty()) {
            new HashSet<>(outerClassMap.entrySet()).stream().filter(entry -> outerClassNodeMap.containsKey(entry.getValue())).forEach(entry -> {
                List<ClassEditor> inners = new ArrayList<>();
                ClassEditor classEditor = new ClassEditor(entry.getKey(), outerClassNodeMap.get(entry.getValue()), inners);
                classEditorClassNodeMap.put(classEditor, entry.getKey());
                classEditorInnerClassMap.get(outerClassNodeMap.get(entry.getValue())).add(classEditor);
                classEditorInnerClassMap.put(classEditor, inners);
                outerClassNodeMap.put(entry.getKey().name, classEditor);
                outerClassMap.remove(entry.getKey());
            });
            if(outerClassMapLen == outerClassMap.size()) throw new MixinFormatException(outerClassMap.keySet().stream().toList().getFirst().name, "outer class in not a mixin class");
            outerClassMapLen = outerClassMap.size();
        }

        Map<ClassNode, Class<?>> mixinClassNodes = new HashMap<>();
        for(Map.Entry<ClassEditor, ClassNode> entry : classEditorClassNodeMap.entrySet().stream().sorted(Comparator.comparing(a -> a.getKey().getName())).toList()) {
            Class<?> targetClass = addClass(entry.getKey(), entry.getValue());

//            PrintWriter pw = new PrintWriter(System.out);
//            entry.getValue().accept(new TraceClassVisitor(null, new Textifier(), pw));
//            pw.flush();

            mixinClassNodes.put(entry.getValue(), targetClass);
        }

        Map<Class<?>, Class<?>> mixinClasses = new HashMap<>();
        for (Map.Entry<ClassNode, Class<?>> entry : mixinClassNodes.entrySet()) {
            Class<?> mixinClass = MixinClassHelper.findClass(entry.getKey().name);
            agent.redefineModule(entry.getValue().getModule(), Stream.of(mixinClass.getModule(), Mixiner.class.getModule()).collect(Collectors.toSet()), Map.of(entry.getValue().getPackageName(), Set.of(mixinClass.getModule())), new HashMap<>(), new HashSet<>(), new HashMap<>());

            mixinClasses.put(mixinClass, entry.getValue());
        }

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

    public static Class<?> addClass(ClassEditor mixinClassEditor, ClassNode mixinClassNode) {
        if(usedMixinClasses.contains(mixinClassEditor.getName())) throw new IllegalArgumentException("Mixin class " + mixinClassEditor.getName() + " used twice");
        usedMixinClasses.add(mixinClassEditor.getName());

        Class<?> targetClass = getTargetClass(mixinClassEditor);
        mixin(targetClass, mixinClassEditor, mixinClassNode);
        return targetClass;
    }

    private static void mixin(Class<?> targetClass, ClassEditor mixinClassEditor, ClassNode mixinClassNode) {
        ClassWriter targetClassWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        if(!classEditors.containsKey(targetClass.getName())) {
            byte[] targetBytecode = Agent.getBytecode(targetClass);
            ClassReader targetClassReader = new ClassReader(targetBytecode);
            ClassNode targetClassNode = new ClassNode();
            targetClassReader.accept(targetClassNode, 0);
            classEditors.put(targetClass.getName(), new ClassEditor(targetClassNode, null, List.of()));
            classEditorNodes.put(classEditors.get(targetClass.getName()), targetClassNode);
        }
        ClassEditor targetClassEditor = classEditors.get(targetClass.getName());
        ClassNode targetClassNode = classEditorNodes.get(targetClassEditor);

        for (AnnotationEditor annotationEditor : mixinClassEditor.getAnnotationEditors()) {
            classTransformers.getOrDefault(annotationEditor.getDesc(), dummy).transform(mixinClassEditor, annotationEditor.getAnnotation(), targetClassEditor);
        }

        for (MethodEditor methodEditor : mixinClassEditor.getMethodEditors()) {
            for (AnnotationEditor annotationNode : methodEditor.getAnnotationEditors()) {
                methodTransformers.getOrDefault(annotationNode.getDesc(), dummy).transform(mixinClassEditor, mixinClassEditor.getMethodEditor(methodEditor.getName() + methodEditor.getDesc()), annotationNode.getAnnotation(), targetClassEditor);
            }
        }

        for (FieldEditor fieldEditor : mixinClassEditor.getFieldEditors()) {
            for (AnnotationEditor annotationNode : fieldEditor.getAnnotationEditors()) {
                fieldTransformers.getOrDefault(annotationNode.getDesc(), dummy).transform(mixinClassEditor, mixinClassEditor.getFieldEditor(fieldEditor.getName()), annotationNode.getAnnotation(), targetClassEditor);
            }
        }

//        PrintWriter pw = new PrintWriter(System.out);
//        targetClassNode.accept(new TraceClassVisitor(null, new Textifier(), pw));
//        pw.flush();

        targetClassNode.accept(targetClassWriter);
        try {
            ClassWriter mixinClassWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            mixinClassNode.accept(mixinClassWriter);
            new MixinerTransformer(mixinClassNode.name, mixinClassWriter.toByteArray());

            agent.redefineClasses(new ClassDefinition(targetClass, targetClassWriter.toByteArray()));
        } catch (UnmodifiableClassException | ClassNotFoundException e) {
            throw new MixinFormatException(mixinClassEditor.getName(), "invalid @Mixin target");
        }
    }

    private static final class Dummy implements ClassTransformer<Annotation>, FieldTransformer<Annotation>, MethodTransformer<Annotation> {

        @Override
        public void transform(ClassEditor mixinClassEditor, Annotation mixinAnnotation, ClassEditor targetClassEditor) {

        }

        @Override
        public void transform(ClassEditor mixinClassEditor, FieldEditor mixinFieldEditor, Annotation mixinAnnotation, ClassEditor targetClassEditor) {

        }

        @Override
        public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Annotation mixinAnnotation, ClassEditor targetClassEditor) {

        }
    }

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
