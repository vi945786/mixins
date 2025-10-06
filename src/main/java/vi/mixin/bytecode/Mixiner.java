package vi.mixin.bytecode;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.classtypes.ClassNodeHierarchy;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.classtypes.accessortype.AccessorMixinClassType;
import vi.mixin.api.classtypes.targeteditors.TargetClassManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetFieldManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetInsnListManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetMethodManipulator;
import vi.mixin.api.editors.*;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.classtypes.mixintype.MixinMixinClassType;
import vi.mixin.api.util.TransformerHelper;

import java.lang.annotation.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static vi.mixin.bytecode.Agent.agent;

public class Mixiner {

    private final TargetInsnListManipulator.OpcodeStates opcodeStates = new TargetInsnListManipulator.OpcodeStates();

    private final Map<String, Class<MixinClassType<Annotation, ?, ?, ?, ?>>> mixinClassTypes = new HashMap<>();
    private final Map<String, List<BuiltTransformer>> transformers = new HashMap<>();

    private final Map<String, ClassNode> modifyTargetClassNodes = new HashMap<>();
    private final Set<String> usedMixinClasses = new HashSet<>();

    Mixiner() {
        mixinClassTypes.put(Type.getDescriptor(Mixin.class), null);
    }

    private MixinClassType<Annotation, ?, ?, ?, ?> getMixinClassTypeInstance(String annotationDesc) {
        try {
            Class<MixinClassType<Annotation, ?, ?, ?, ?>> mixinClassTypeClass = mixinClassTypes.get(annotationDesc);
            if(mixinClassTypeClass == null) return null;
            Constructor<MixinClassType<Annotation, ?, ?, ?, ?>> c = mixinClassTypeClass.getConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

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

    public void addMixinClassType(Class<MixinClassType<Annotation, ?, ?, ?, ?>> mixinClassType) {
        for(java.lang.reflect.Type type : mixinClassType.getGenericInterfaces()) {
            if(!type.getTypeName().split("<")[0].equals(MixinClassType.class.getName())) continue;
            if(!(type instanceof ParameterizedType pt)) throw new MixinFormatException(mixinClassType.getName(), "mixin class type must pass generic parameters to the to MixinClassType interface");

            String annotationDesc = "L" + pt.getActualTypeArguments()[0].getTypeName().split("<")[0].replace(".", "/") + ";";

            if(mixinClassTypes.containsKey(annotationDesc)) throw new IllegalArgumentException(annotationDesc + " is already registered for a mixin class type annotation");
            mixinClassTypes.put(annotationDesc, mixinClassType);
            break;
        }
    }

    public void addBuiltTransformer(BuiltTransformer transformer) {
        String annotationDesc = "L" + transformer.annotation().getName().replace(".", "/") + ";";

        if(transformers.getOrDefault(annotationDesc, List.of()).stream().anyMatch(t -> t.mixinClassType().getName().equals(transformer.mixinClassType().getName()) && t.isAnnotatedMethod() == transformer.isAnnotatedMethod()))
            throw new IllegalArgumentException(transformer.annotation().getName() + " is already registered for mixin type " + transformer.mixinClassType().getName());
        transformers.computeIfAbsent(annotationDesc, (k) -> new ArrayList<>()).add(transformer);
    }

    private static Class<?> loadMixinClass(ClassNode mixinClassNode, Class<?> targetClass) {
        ClassWriter mixinWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        mixinClassNode.accept(mixinWriter);
        new MixinerTransformer(mixinClassNode.name, mixinWriter.toByteArray());

        Class<?> mixinClass = MixinClassHelper.findClass(mixinClassNode.name);
        assert mixinClass != null;
        agent.redefineModule(targetClass.getModule(), Stream.of(mixinClass.getModule(), Mixiner.class.getModule()).collect(Collectors.toSet()), Map.of(targetClass.getPackageName(), Set.of(mixinClass.getModule())), new HashMap<>(), new HashSet<>(), new HashMap<>());

        return mixinClass;
    }

        public void addClasses(Collection<byte[]> mixins, Collection<byte[]> anonymousInners) {
        Map<ClassNode, String> outerClassMap = new HashMap<>();

        //parse mixin classes
        for (byte[] bytecode : mixins) {
            ClassNode node = readClassNode(bytecode);
            String outerName = node.innerClasses.stream()
                    .filter(inner -> inner.name.equals(node.name))
                    .map(inner -> inner.outerName)
                    .findFirst().orElse(null);
            outerClassMap.put(node, outerName);
        }

        //parse anonymous inner classes
        for (byte[] bytecode : anonymousInners) {
            ClassNode node = readClassNode(bytecode);
            node.invisibleAnnotations = new ArrayList<>();
            String outerName = node.outerClass;
            outerClassMap.put(node, outerName);

            AnnotationNode mixinAnno = new AnnotationNode(Type.getDescriptor(Mixin.class));
            mixinAnno.values = List.of("value", Type.getType("L" + node.name + ";"));
            node.invisibleAnnotations.add(mixinAnno);
            node.invisibleAnnotations.add(
                    new AnnotationNode("Lvi/mixin/bytecode/anonymoustype/AnonymousMixinClassType$Anonymous;")
            );
        }

        mixins.clear();
        anonymousInners.clear();

        //build class hierarchies
        Map<String, ClassNodeHierarchy> outerToHierarchy = new HashMap<>();
        Map<ClassNodeHierarchy, List<ClassNodeHierarchy>> hierarchyChildren = new HashMap<>();
        outerToHierarchy.put(null, null);
        hierarchyChildren.put(null, new ArrayList<>());

        buildHierarchy(outerClassMap, outerToHierarchy, hierarchyChildren);

        outerToHierarchy.clear();
        outerClassMap.clear();
        hierarchyChildren.remove(null);

        //Map mixins to target classes
        Map<Class<?>, List<ClassNodeHierarchy>> targetToHierarchies = new HashMap<>();
        Map<ClassNode, MixinResult> nodeToResult = new HashMap<>();

        hierarchyChildren.keySet().stream()
                .sorted(Comparator.comparing(a -> a.classNode().name))
                .forEach(hierarchy -> {
                    ClassNode mixinNode = hierarchy.classNode();
                    Class<?> targetClass = getTargetClass(mixinNode);
                    nodeToResult.put(mixinNode, addClass(targetClass, hierarchy));
                    targetToHierarchies.computeIfAbsent(targetClass, k -> new ArrayList<>()).add(hierarchy);
                });

        hierarchyChildren.clear();

        //mixin and redefine classes based on the target class
        Map<Class<?>, Class<?>> mixinToTarget = new HashMap<>();
        for (Map.Entry<Class<?>, List<ClassNodeHierarchy>> entry : targetToHierarchies.entrySet()) {
            Class<?> targetClass = entry.getKey();
            Map<ClassNode, Class<?>> nodeToMixinClass = new HashMap<>();

            //redefine and load mixin classes before target redefinition
            entry.getValue().forEach(hierarchy -> {
                ClassNode node = hierarchy.classNode();
                MixinResult result = nodeToResult.get(node);
                if (!result.redefineTargetFirst) {
                    Class<?> mixin = loadMixinClass(node, targetClass);
                    mixinToTarget.put(mixin, targetClass);
                    nodeToMixinClass.put(node, mixin);
                }
            });

            redefineTargetClass(targetClass);

            // redefine and load mixin classes after target redefinition and invoke setup method
            entry.getValue().forEach(hierarchy -> {
                ClassNode node = hierarchy.classNode();
                MixinResult result = nodeToResult.get(node);

                if (result.redefineTargetFirst) {
                    Class<?> mixin = loadMixinClass(node, targetClass);
                    mixinToTarget.put(mixin, targetClass);
                    nodeToMixinClass.put(node, mixin);
                }

                invokeSetupMethod(node, nodeToMixinClass.get(node), result);
            });
        }

        nodeToResult.clear();
        targetToHierarchies.clear();
        validateInnerMixins(mixinToTarget);
    }

    private void buildHierarchy(Map<ClassNode, String> outerClassMap, Map<String, ClassNodeHierarchy> outerToHierarchy, Map<ClassNodeHierarchy, List<ClassNodeHierarchy>> hierarchyChildren) {
        int prevSize;
        do {
            prevSize = outerClassMap.size();
            for (Map.Entry<ClassNode, String> entry : new HashSet<>(outerClassMap.entrySet())) {
                String outer = entry.getValue();
                if (!outerToHierarchy.containsKey(outer)) continue;

                List<ClassNodeHierarchy> children = new ArrayList<>();
                ClassNodeHierarchy hierarchy = new ClassNodeHierarchy(entry.getKey(), outerToHierarchy.get(outer), children);
                hierarchyChildren.get(outerToHierarchy.get(outer)).add(hierarchy);
                hierarchyChildren.put(hierarchy, children);
                outerToHierarchy.put(entry.getKey().name, hierarchy);
                outerClassMap.remove(entry.getKey());
            }
            if (prevSize == outerClassMap.size() && !outerClassMap.isEmpty())
                throw new MixinFormatException(outerClassMap.keySet().iterator().next().name, "outer class is not a mixin class");
        } while (!outerClassMap.isEmpty());
    }

    private void redefineTargetClass(Class<?> targetClass) {
        try {
            ClassNode node = modifyTargetClassNodes.get(targetClass.getName());
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            agent.redefineClasses(new ClassDefinition(targetClass, writer.toByteArray()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeSetupMethod(ClassNode mixinNode, Class<?> mixinClass, MixinResult result) {
        if (result.setUpMethodName == null || result.setUpMethodName.isEmpty()) return;
        try {
            Method method = mixinClass.getMethod(result.setUpMethodName);
            if ((method.getModifiers() & Opcodes.ACC_STATIC) == 0)
                throw new MixinFormatException(mixinNode.name, "\"set up\" method must be static");
            method.setAccessible(true);
            method.invoke(null);
        } catch (NoSuchMethodException e) {
            throw new MixinFormatException(mixinNode.name, "\"set up\" method not found (must be static, no params)");
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void validateInnerMixins(Map<Class<?>, Class<?>> mixinToTarget) {
        for (Map.Entry<Class<?>, Class<?>> entry : mixinToTarget.entrySet()) {
            Class<?> mixin = entry.getKey();
            Class<?> target = entry.getValue();

            for (Class<?> inner : mixin.getDeclaredClasses()) {
                if (!mixinToTarget.containsKey(inner))
                    throw new MixinFormatException(inner.getName(), "inner class of mixin is not a mixin class");
            }

            if (!mixin.isMemberClass()) continue;

            Class<?> outer = mixin.getEnclosingClass();
            Class<?> outerTarget = mixinToTarget.get(outer);
            boolean match = Arrays.stream(outerTarget.getDeclaredClasses())
                    .anyMatch(c -> c == target);
            if (!match)
                throw new MixinFormatException(mixin.getName().replace(".", "/"), "inner target " + target + " is not an inner class of target " + outerTarget);
        }
    }

    private ClassNode readClassNode(byte[] bytecode) {
        ClassReader reader = new ClassReader(bytecode);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        return node;
    }

    private ClassNode cloneClassNode(ClassNode original) {
        ClassNode clone = new ClassNode();
        original.accept(clone);
        return clone;
    }

    private MixinResult addClass(Class<?> targetClass, ClassNodeHierarchy mixinClassNodeHierarchy) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.classNode();
        if(usedMixinClasses.contains(mixinClassNode.name)) throw new IllegalArgumentException("Mixin class " + mixinClassNode.name + " used twice");
        usedMixinClasses.add(mixinClassNode.name);

        return mixin(targetClass, mixinClassNodeHierarchy);
    }

    private MixinResult mixin(Class<?> targetClass, ClassNodeHierarchy mixinClassNodeHierarchy) {
        ClassNode originalTargetClassNode = readClassNode(Agent.getBytecode(targetClass));
        modifyTargetClassNodes.computeIfAbsent(targetClass.getName(), k -> cloneClassNode(originalTargetClassNode));
        ClassNode modifyTargetClassNode = modifyTargetClassNodes.get(targetClass.getName());

        ClassNode modifyMixinClassNode = mixinClassNodeHierarchy.classNode();
        ClassNode originalMixinClassNode = cloneClassNode(modifyMixinClassNode);

        MixinTypeInfo mixinTypeInfo = determineMixinClassType(originalMixinClassNode);
        MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType = mixinTypeInfo.type();
        Annotation mixinAnnotation = mixinTypeInfo.annotation();

        processMethodNodes(modifyMixinClassNode.methods, originalMixinClassNode, originalTargetClassNode, modifyTargetClassNode, mixinClassType);
        processFieldNodes(modifyMixinClassNode.fields, originalMixinClassNode, originalTargetClassNode, modifyTargetClassNode, mixinClassType);

        TargetClassManipulator manipulator = new TargetClassManipulator(modifyTargetClassNode, originalTargetClassNode, targetClass, opcodeStates);
        return new MixinResult(mixinClassType.redefineTargetFirst(), mixinClassType.transform(mixinClassNodeHierarchy, mixinAnnotation, manipulator));
    }

    private record MixinTypeInfo(MixinClassType<Annotation, ?, ?, ?, ?> type, Annotation annotation) {}

    private MixinTypeInfo determineMixinClassType(ClassNode mixinClassNode) {
        Annotation annotation = null;
        for (AnnotationNode annotationNode : mixinClassNode.invisibleAnnotations) {
            if (!mixinClassTypes.containsKey(annotationNode.desc)) continue;

            annotation = TransformerHelper.getAnnotation(annotationNode);
            MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType = getMixinClassTypeInstance(annotationNode.desc);
            if (mixinClassType != null) {
                return new MixinTypeInfo(mixinClassType, annotation);
            }
        }

        MixinClassType<Annotation, ?, ?, ?, ?> defaultType = (mixinClassNode.access & Opcodes.ACC_INTERFACE) == 0 ? new MixinMixinClassType() : new AccessorMixinClassType();

        return new MixinTypeInfo(defaultType, annotation);
    }

    private void processMethodNodes(List<MethodNode> methods, ClassNode originalMixin, ClassNode originalTarget, ClassNode modifyTarget, MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType) {
        if (methods == null) return;
        for (MethodNode method : methods) {
            if (method.invisibleAnnotations == null) {
                mixinClassType.create(method, null);
                continue;
            }
            for (AnnotationNode annotation : method.invisibleAnnotations) {
                transformNode(annotation, method, originalMixin, originalTarget, modifyTarget, mixinClassType, true);
            }
        }
    }

    private void processFieldNodes(List<FieldNode> fields, ClassNode originalMixin, ClassNode originalTarget, ClassNode modifyTarget, MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType) {
        if (fields == null) return;
        for (FieldNode field : fields) {
            if (field.invisibleAnnotations == null) {
                mixinClassType.create(field, null);
                continue;
            }
            for (AnnotationNode annotation : field.invisibleAnnotations) {
                transformNode(annotation, field, originalMixin, originalTarget, modifyTarget, mixinClassType, false);
            }
        }
    }

    private void transformNode(AnnotationNode annotationNode, Object node, ClassNode originalMixin, ClassNode originalTarget, ClassNode modifyTarget, MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType, boolean isMethod) {
        List<BuiltTransformer> transformers = this.transformers.getOrDefault(annotationNode.desc, List.of());
        BuiltTransformer builtTransformer = transformers.stream()
                .filter(t -> t.mixinClassType().getName().equals(mixinClassType.getClass().getName()) && t.isAnnotatedMethod() == isMethod)
                .findAny().orElse(null);
        if (builtTransformer == null) return;

        String name = originalMixin.name + "." + (isMethod ? ((MethodNode) node).name + ((MethodNode) node).desc : ((FieldNode) node).name);
        Annotation annotation = TransformerHelper.getAnnotation(annotationNode);
        ClassNode mixinClone = cloneClassNode(originalMixin);
        ClassNode targetClone = cloneClassNode(originalTarget);

        if (builtTransformer.isTargetMethod()) {
            TargetMethodManipulator target = getTargetMethodEditor(builtTransformer, name, node, originalTarget, modifyTarget, annotation);
            AnnotatedEditor annotatedEditor = createAnnotatedEditor(mixinClassType, node, target);
            TargetEditor targetEditor = mixinClassType.create(target, annotatedEditor);
            builtTransformer.transformFunction().transform(annotatedEditor, targetEditor, annotation, mixinClone, targetClone);
        } else {
            TargetFieldManipulator target = getFieldTargetEditor(builtTransformer, name, node, originalTarget, modifyTarget, annotation);
            AnnotatedEditor annotatedEditor = createAnnotatedEditor(mixinClassType, node, target);
            TargetFieldEditor targetEditor = mixinClassType.create(target, annotatedEditor);
            builtTransformer.transformFunction().transform(annotatedEditor, targetEditor, annotation, mixinClone, targetClone);
        }
    }

    private AnnotatedEditor createAnnotatedEditor(MixinClassType<?, ?, ?, ?, ?> mixinClassType, Object node, Object manipulator) {
        if(node instanceof MethodNode methodNode) {
            return mixinClassType.create(methodNode, manipulator);
        } else if(node instanceof FieldNode fieldNode) {
            return mixinClassType.create(fieldNode, manipulator);
        }

        throw new UnsupportedOperationException();
    }

    private <A extends Annotation> TargetMethodManipulator getTargetMethodEditor(BuiltTransformer builtTransformer, String name, Object node, ClassNode originalTargetClassNode, ClassNode modifyTargetClassNode, A annotation) {
        Map<String, MethodNode> modifyTargetMethodNodes = new HashMap<>();
        modifyTargetClassNode.methods.forEach(m -> modifyTargetMethodNodes.put(m.name + m.desc, m));

        for(MethodNode m : originalTargetClassNode.methods) {
            if(builtTransformer.targetFilter().isTarget(cloneNode(node), cloneNode(m), annotation))
                return new TargetMethodManipulator(originalTargetClassNode.name, modifyTargetMethodNodes.get(m.name + m.desc), m, opcodeStates);
        }
        throw new MixinFormatException(name, "doesn't have a target");
    }

    private <A extends Annotation> TargetFieldManipulator getFieldTargetEditor(BuiltTransformer builtTransformer, String name, Object node, ClassNode originalTargetClassNode, ClassNode modifyTargetClassNode, A annotation) {
        Map<String, FieldNode> modifyFieldNodes = new HashMap<>();
        modifyTargetClassNode.fields.forEach(f -> modifyFieldNodes.put(f.name, f));

        for(FieldNode f : originalTargetClassNode.fields) {
            if(builtTransformer.targetFilter().isTarget(cloneNode(node), cloneNode(f), annotation))
                return new TargetFieldManipulator(modifyFieldNodes.get(f.name), f);
        }
        throw new MixinFormatException(name, "doesn't have a target");
    }

    private Object cloneNode(Object node) {
        ClassNode cloneMixin = new ClassNode();
        if(node instanceof MethodNode methodNode) {
            methodNode.accept(cloneMixin);
            return cloneMixin.methods.getFirst();
        } else if(node instanceof FieldNode fieldNode) {
            fieldNode.accept(cloneMixin);
            return cloneMixin.fields.getFirst();
        }

        throw new UnsupportedOperationException();
    }

    private record MixinResult(boolean redefineTargetFirst, String setUpMethodName) {}

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