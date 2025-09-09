package vi.mixin.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.annotations.fields.Mutable;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.*;
import vi.mixin.bytecode.Transformers.*;
import vi.mixin.util.AnnotationNodeToInstance;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static vi.mixin.bytecode.Agent.agent;

public class Mixiner {

    private static final String mixinDesc = "L" + Mixin.class.getName().replace(".", "/") + ";";
    private static final String extendsDesc = "L" + Extends.class.getName().replace(".", "/") + ";";

    private static final Map<String, ClassTransformer<?>> classTransformers = new HashMap<>();
    private static final Map<String, FieldTransformer<?>> fieldTransformers = new HashMap<>();
    private static final Map<String, MethodTransformer<?>> methodTransformers = new HashMap<>();
    private static final Dummy dummy = new Dummy();

    private static final Map<String, ClassEditor> classEditors = new HashMap<>();
    private static final Map<ClassEditor, ClassNode> classEditorNodes = new HashMap<>();
    private static final Set<String> usedMixinClasses = new HashSet<>();

    static {
        addMixinTransformer(new ExtendsTransformer(), Extends.class);
        addMixinTransformer(new GetterTransformer(), Getter.class);
        addMixinTransformer(new InjectTransformer(), Inject.class);
        addMixinTransformer(new InvokerTransformer(), Invoker.class);
        addMixinTransformer(new MutableTransformer(), Mutable.class);
        addMixinTransformer(new OverridableTransformer(), Overridable.class);
        addMixinTransformer(new SetterTransformer(), Setter.class);
        addMixinTransformer(new ShadowTransformer(), Shadow.class);
    }

    public static void addMixinTransformer(MixinTransformer transformer, Class<? extends Annotation> annotation) {
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

    private static Class<?> getTargetClass(ClassNode mixinClassNode) {
        if(mixinClassNode.visibleAnnotations == null) throw new MixinFormatException("Mixin class " + mixinClassNode.name + " doesn't have a @Mixin annotation");
        for (AnnotationNode visibleAnnotation : mixinClassNode.visibleAnnotations) {
            if(visibleAnnotation.desc.equals(mixinDesc)) {
                return AnnotationNodeToInstance.<Mixin>getAnnotation(visibleAnnotation).value();
            }
        }

        throw new IllegalStateException("Mixin class " + mixinClassNode.name + " has @Mixin annotation but it couldn't be found");
    }

    public static void addClasses(byte[] mixinClass) throws ClassNotFoundException, UnmodifiableClassException, IOException {
        Map<Class<?>, List<ClassNode>> mixinTargetMap = new HashMap<>();
        ClassReader mixinClassReader = new ClassReader(mixinClass);
        ClassNode mixinClassNode = new ClassNode();
        mixinClassReader.accept(mixinClassNode, 0);

        if(usedMixinClasses.contains(mixinClassNode.name)) throw new IllegalArgumentException("Mixin class " + mixinClassNode.name + " used twice");
        usedMixinClasses.add(mixinClassNode.name);

        Class<?> targetClass = getTargetClass(mixinClassNode);
        mixinTargetMap.computeIfAbsent(targetClass, c -> new ArrayList<>()).add(mixinClassNode);

       mixin(targetClass, mixinClassNode);
    }

    private static void mixin(Class<?> targetClass, ClassNode mixinClassNode) throws UnmodifiableClassException, ClassNotFoundException {
        ClassWriter targetClassWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        if(!classEditors.containsKey(targetClass.getName())) {
            byte[] targetBytecode = Agent.getBytecode(targetClass);
            ClassReader targetClassReader = new ClassReader(targetBytecode);
            ClassNode targetClassNode = new ClassNode();
            targetClassReader.accept(targetClassNode, 0);
            classEditors.put(targetClass.getName(), new ClassEditor(targetClassNode));
            classEditorNodes.put(classEditors.get(targetClass.getName()), targetClassNode);
        }
        ClassEditor targetClassEditor = classEditors.get(targetClass.getName());
        ClassNode targetClassNode = classEditorNodes.get(targetClassEditor);

        ClassWriter mixinClassWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassEditor mixinClassEditor = new ClassEditor(mixinClassNode);
        boolean isExtend = false;
        boolean isInterface = (mixinClassNode.access & Opcodes.ACC_INTERFACE) != 0;

        if (isInterface) {
            targetClassNode.interfaces.add(mixinClassNode.name);
        }

        for (AnnotationNode annotationNode : mixinClassNode.visibleAnnotations) {
            if(annotationNode.desc.equals(extendsDesc)) isExtend = true;
            classTransformers.getOrDefault(annotationNode.desc, dummy).transform(mixinClassEditor, AnnotationNodeToInstance.getAnnotation(annotationNode), targetClassEditor);
        }

        for (MethodNode methodNode : List.copyOf(mixinClassNode.methods)) {
            if (!isInterface && !isExtend && (methodNode.access & Opcodes.ACC_PRIVATE) == 0 && !methodNode.name.equals("<init>")) throw new MixinFormatException("method " + mixinClassNode.name + "." + methodNode.name + " must be private");
            if(methodNode.visibleAnnotations == null) continue;
            for (AnnotationNode annotationNode : methodNode.visibleAnnotations) {
                methodTransformers.getOrDefault(annotationNode.desc, dummy).transform(mixinClassEditor, mixinClassEditor.getMethodEditor(methodNode.name + methodNode.desc), AnnotationNodeToInstance.getAnnotation(annotationNode), targetClassEditor);
            }
        }

        for (FieldNode fieldNode : List.copyOf(mixinClassNode.fields)) {
            if (!isInterface && !isExtend && (fieldNode.access & Opcodes.ACC_PRIVATE) == 0) throw new MixinFormatException("field " + mixinClassNode.name + "." + fieldNode.name + " must be private");
            if(fieldNode.visibleAnnotations == null) continue;
            for (AnnotationNode annotationNode : fieldNode.visibleAnnotations) {
                fieldTransformers.getOrDefault(annotationNode.desc, dummy).transform(mixinClassEditor, mixinClassEditor.getFieldEditor(fieldNode.name), AnnotationNodeToInstance.getAnnotation(annotationNode), targetClassEditor);
            }
        }

//        PrintWriter pw = new PrintWriter(System.out);
//        targetClassNode.accept(new TraceClassVisitor(null, new Textifier(), pw));
//        pw.flush();

        targetClassNode.accept(targetClassWriter);
        agent.redefineClasses(new ClassDefinition(targetClass, targetClassWriter.toByteArray()));

        mixinClassNode.accept(mixinClassWriter);
        new MixinerTransformer(mixinClassNode.name, mixinClassWriter.toByteArray());
        Class<?> mixinClass = MixinClassHelper.findClass(mixinClassNode.name);
        agent.redefineModule(targetClass.getModule(), Stream.of(mixinClass.getModule(), Mixiner.class.getModule()).collect(Collectors.toSet()), Map.of(targetClass.getPackageName(), Set.of(mixinClass.getModule())), new HashMap<>(), new HashSet<>(), new HashMap<>());
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
