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
    private final Map<String, ClassNode> originalMixinClassNodes = new HashMap<>();
    private final Map<String, ClassNode> targetSuperMap = new HashMap<>();
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

    public static String getTargetClass(ClassNode mixinClassNode) {
        for (AnnotationNode annotationNode : mixinClassNode.invisibleAnnotations) {
            if(annotationNode.desc.equals(Type.getType(Mixin.class).getDescriptor())) {
                if(annotationNode.values.isEmpty()) throw new MixinFormatException(mixinClassNode.name, "@Mixin doesn't have a target");
                if(annotationNode.values.size() == 4) throw new MixinFormatException(mixinClassNode.name, "@Mixin \"value\" and \"name\" set");

                String target;
                if(annotationNode.values.get(1) instanceof Type type) {
                    target = type.getInternalName();
                } else if (annotationNode.values.get(1) instanceof String s) {
                    target = s;
                } else {
                    throw new MixinFormatException(mixinClassNode.name, "@Mixin has an illegal value");
                }

                if(target.endsWith(Type.getInternalName(Object.class))) throw new MixinFormatException(mixinClassNode.name, "@Mixin target cannot be Object");
                if(target.equals(mixinClassNode.name)) throw new MixinFormatException(mixinClassNode.name, "@Mixin cannot target itself");
                return target;
            }
        }

        throw new MixinFormatException(mixinClassNode.name, "doesn't have a @Mixin annotation");
    }

    public void mapTargetSupers(ClassNode target) {
        while(!target.superName.equals(Type.getInternalName(Object.class)) && !targetSuperMap.containsKey(target.name)) {
            ClassNode targetSuper = readClassNode(MixinClassHelper.getBytecode(target.superName));
            targetSuperMap.put(target.name, targetSuper);
            target = targetSuper;
        }
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

    private static Class<?> loadMixinClass(ClassNode mixinClassNode) {
        ClassWriter mixinWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        mixinClassNode.accept(mixinWriter);
        new MixinerTransformer(mixinClassNode.name, mixinWriter.toByteArray());

        Class<?> mixinClass = MixinClassHelper.findClass(mixinClassNode.name);
        assert mixinClass != null;

        return mixinClass;
    }

    public void addClasses(Collection<byte[]> mixins, Collection<byte[]> anonymousInners) {
        Map<ClassNode, String> outerClassMap = new HashMap<>();

        for (byte[] bytecode : mixins) {
            ClassNode node = readClassNode(bytecode);
            String outerName = node.innerClasses.stream()
                    .filter(inner -> inner.name.equals(node.name))
                    .map(inner -> inner.outerName)
                    .findFirst().orElse(null);
            outerClassMap.put(node, outerName);

            originalMixinClassNodes.put(node.name, cloneClassNode(node));
        }

        for (byte[] bytecode : anonymousInners) {
            ClassNode node = readClassNode(bytecode);
            node.invisibleAnnotations = new ArrayList<>();
            String outerName = node.outerClass;
            outerClassMap.put(node, outerName);

            AnnotationNode mixinAnno = new AnnotationNode(Type.getDescriptor(Mixin.class));
            mixinAnno.values = List.of("value", Type.getType("Lvi/mixin/bytecode/anonymoustype/AnonymousTarget;"));
            node.invisibleAnnotations.add(mixinAnno);
            node.invisibleAnnotations.add(
                    new AnnotationNode("Lvi/mixin/bytecode/anonymoustype/AnonymousMixinClassType$Anonymous;")
            );

            originalMixinClassNodes.put(node.name, cloneClassNode(node));
        }

        mixins.clear();
        anonymousInners.clear();

        List<List<ClassNodeHierarchy>> orderedHierarchies = buildHierarchies(outerClassMap);
        outerClassMap.clear();

        Map<String, PreMixinResult> mixinNameToPreMixinResult = new HashMap<>();
        orderedHierarchies.forEach(hierarchies -> hierarchies.stream()
            .sorted(Comparator.comparing(a -> a.mixinNode().name))
            .forEach(hierarchy -> mixinNameToPreMixinResult.put(hierarchy.mixinNode().name, preMixin(hierarchy))));

        Map<String, MixinResult> nodeNameToResult = new HashMap<>();
        orderedHierarchies.forEach(hierarchies -> hierarchies.stream()
                .sorted(Comparator.comparing(a -> a.mixinNode().name))
                .forEach(hierarchy -> {
                    ClassNode mixinNode = hierarchy.mixinNode();

                    PreMixinResult targetPreMixinResult = mixinNameToPreMixinResult.get(hierarchy.targetNodeClone().name);
                    if(targetPreMixinResult != null && !targetPreMixinResult.mixinClassType.isAllowedAsTarget()) throw new MixinFormatException(mixinNode.name, "target class of type " + targetPreMixinResult.mixinClassType.getClass().getName() + " does not allow being a target");

                    nodeNameToResult.put(mixinNode.name, addClass(hierarchy, mixinNameToPreMixinResult.get(mixinNode.name)));
                }));

        Map<ClassNode, Class<?>> nodeToMixinClass = new HashMap<>();
        orderedHierarchies.reversed().forEach(hierarchies -> hierarchies.forEach(hierarchy -> {
            MixinResult result = nodeNameToResult.get(hierarchy.mixinNode().name);
            if (!result.redefineTargetFirst) {
                Class<?> mixinClass = loadMixinClass(hierarchy.mixinNode());
                nodeToMixinClass.put(hierarchy.mixinNode(), mixinClass);
            }
        }));

        Map<Class<?>, Class<?>> mixinToTarget = orderedHierarchies.stream().flatMap(Collection::stream)
                .filter(hierarchy -> nodeToMixinClass.containsKey(hierarchy.mixinNode()))
                .map(hierarchy -> {
                    String targetName = hierarchy.targetNodeClone().name;
                    if (usedMixinClasses.contains(targetName) && nodeNameToResult.get(hierarchy.targetNodeClone().name).redefineTargetFirst)
                        return null;

                    Class<?> mixinClass = nodeToMixinClass.get(hierarchy.mixinNode());
                    Class<?> targetClass = Objects.requireNonNull(MixinClassHelper.findClass(targetName));
                    agent.redefineModule(targetClass.getModule(), Stream.of(mixinClass.getModule(), Mixiner.class.getModule()).collect(Collectors.toSet()), Map.of(targetClass.getPackageName(), Set.of(mixinClass.getModule())), new HashMap<>(), new HashSet<>(), new HashMap<>());
                    return Map.entry(mixinClass, targetClass);
                }).filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        redefineTargetClasses();

        orderedHierarchies.reversed().forEach(hierarchies -> hierarchies.forEach(hierarchy -> {
            MixinResult result = nodeNameToResult.get(hierarchy.mixinNode().name);

            if (result.redefineTargetFirst) {
                Class<?> mixinClass = loadMixinClass(hierarchy.mixinNode());
                Class<?> targetClass = Objects.requireNonNull(MixinClassHelper.findClass(hierarchy.targetNodeClone().name));
                agent.redefineModule(targetClass.getModule(), Stream.of(mixinClass.getModule(), Mixiner.class.getModule()).collect(Collectors.toSet()), Map.of(targetClass.getPackageName(), Set.of(mixinClass.getModule())), new HashMap<>(), new HashSet<>(), new HashMap<>());
                nodeToMixinClass.put(hierarchy.mixinNode(), mixinClass);
                mixinToTarget.put(mixinClass, targetClass);
            }

            invokeSetupMethod(hierarchy.mixinNode(), nodeToMixinClass.get(hierarchy.mixinNode()), result);
        }));

        nodeToMixinClass.clear();
        nodeNameToResult.clear();
        validateInnerMixins(mixinToTarget);
    }

    private ClassNodeHierarchy getTargetParent(ClassNode child, ArrayList<ClassNodeHierarchy> children) {
        if(child == null) return null;
        ClassNode parentNode = child.innerClasses.stream().filter(inner -> inner.name.equals(child.name))
                .map(inner -> inner.outerName)
                .filter(Objects::nonNull)
                .map(MixinClassHelper::getBytecode)
                .map(Mixiner::readClassNode).findAny().orElse(null);
        if(parentNode == null) return null;
        ArrayList<ClassNodeHierarchy> nextParentChildren = new ArrayList<>();
        ClassNodeHierarchy parent = new ClassNodeHierarchy(null, parentNode, getTargetParent(parentNode, nextParentChildren), children);
        nextParentChildren.add(parent);
        return parent;
    }

    private ArrayList<ClassNodeHierarchy> getTargetChildren(ClassNode parentNode, ClassNodeHierarchy parent) {
        if(parentNode == null) return null;
        Map<ClassNode, ArrayList<ClassNodeHierarchy>> nextChildrenMap = new HashMap<>();
        Map<ClassNode, ClassNodeHierarchy> childrenMap = parentNode.innerClasses.stream().filter(inner -> inner.outerName != null && inner.outerName.equals(parentNode.name))
                .map(inner -> inner.name)
                .map(MixinClassHelper::getBytecode)
                .map(Mixiner::readClassNode)
                .map(child -> Map.entry(child, new ClassNodeHierarchy(null, child, parent, nextChildrenMap.put(child, new ArrayList<>()))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        nextChildrenMap.forEach((child, nextChildren) -> nextChildren.addAll(getTargetChildren(child, childrenMap.get(child))));

        return new ArrayList<>(childrenMap.values());
    }

    private List<List<ClassNodeHierarchy>> buildHierarchies(Map<ClassNode, String> outerClassMap) {
        Map<String, ClassNodeHierarchy> outerToHierarchy = new HashMap<>();
        outerToHierarchy.put(null, null);

        Set<ClassNodeHierarchy> classNodeHierarchies = new HashSet<>();
        Map<ClassNodeHierarchy, ArrayList<ClassNodeHierarchy>> hierarchyChildren = new HashMap<>();
        hierarchyChildren.put(null, new ArrayList<>());

        Map<String, ClassNode> targetNodes = new HashMap<>();
        Map<String, ClassNode> mixinNodes = new HashMap<>();
        Map<ClassNode, ClassNode> mixinToTarget = new HashMap<>();

        outerClassMap.keySet().forEach(node -> mixinNodes.put(node.name, node));

        int prevSize;
        do {
            prevSize = outerClassMap.size();
            for (Map.Entry<ClassNode, String> entry : new HashSet<>(outerClassMap.entrySet())) {
                String outer = entry.getValue();
                ClassNode mixinNode = entry.getKey();
                if (!outerToHierarchy.containsKey(outer)) continue;

                ClassNode target = targetNodes.computeIfAbsent(getTargetClass(mixinNode), name -> readClassNode(MixinClassHelper.getBytecode(name)));
                if(mixinNodes.containsKey(target.name)) target = mixinNodes.get(target.name);
                modifyTargetClassNodes.putIfAbsent(target.name, target);

                mixinToTarget.put(mixinNode, target);

                ClassNodeHierarchy parent = outerToHierarchy.get(outer);
                if(parent == null) {
                    ArrayList<ClassNodeHierarchy> children = new ArrayList<>();
                    parent = getTargetParent(target, children);
                    hierarchyChildren.put(parent, children);
                }

                ArrayList<ClassNodeHierarchy> children = new ArrayList<>();
                ClassNodeHierarchy hierarchy = new ClassNodeHierarchy(mixinNode, cloneClassNode(target), parent, children);
                children.addAll(getTargetChildren(target, hierarchy));
                classNodeHierarchies.add(hierarchy);
                hierarchyChildren.get(parent).add(hierarchy);
                hierarchyChildren.put(hierarchy, children);
                outerToHierarchy.put(mixinNode.name, hierarchy);
                outerClassMap.remove(mixinNode);
            }
            if (prevSize == outerClassMap.size() && !outerClassMap.isEmpty())
                throw new MixinFormatException(outerClassMap.keySet().iterator().next().name, "outer class is not a mixin class");
        } while (!outerClassMap.isEmpty());

        hierarchyChildren.remove(null);

        Map<ClassNodeHierarchy, Integer> hierarchyOrderMap = new HashMap<>();
        classNodeHierarchies.forEach(hierarchy -> {
            ClassNode current = hierarchy.mixinNode();
            int i = 0;
            for (; current != null; i++) {
                current = mixinToTarget.get(current);
            }
            hierarchyOrderMap.put(hierarchy, i);
        });

        Map<Integer, List<ClassNodeHierarchy>> orderMap = new HashMap<>();
        hierarchyOrderMap.values().stream().distinct().forEach(i -> orderMap.put(i, new ArrayList<>()));
        hierarchyOrderMap.forEach((hierarchy, order) -> orderMap.get(order).add(hierarchy));

        return orderMap.entrySet().stream().sorted(Comparator.comparingInt(Map.Entry::getKey)).map(Map.Entry::getValue).toList().reversed();
    }

    private void redefineTargetClasses() {
        ClassDefinition[] classDefinitions = modifyTargetClassNodes.values().stream().filter(node -> !usedMixinClasses.contains(node.name)).map(node -> {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return new ClassDefinition(Objects.requireNonNull(MixinClassHelper.findClass(node.name)), writer.toByteArray());
        }).toArray(ClassDefinition[]::new);

        try {
            agent.redefineClasses(classDefinitions);
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

    private static ClassNode readClassNode(byte[] bytecode) {
        ClassReader reader = new ClassReader(bytecode);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_FRAMES);
        return node;
    }

    private static ClassNode cloneClassNode(ClassNode original) {
        ClassNode clone = new ClassNode();
        original.accept(clone);
        return clone;
    }

    private MixinResult addClass(ClassNodeHierarchy mixinClassNodeHierarchy, PreMixinResult preMixinResult) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.mixinNode();
        if(usedMixinClasses.contains(mixinClassNode.name)) throw new IllegalArgumentException("Mixin class " + mixinClassNode.name + " used twice");
        usedMixinClasses.add(mixinClassNode.name);

        return mixin(mixinClassNodeHierarchy, preMixinResult);
    }

    private PreMixinResult preMixin(ClassNodeHierarchy mixinClassNodeHierarchy) {
        ClassNode originalTargetClassNode = mixinClassNodeHierarchy.targetNodeClone();
        ClassNode modifyTargetClassNode = modifyTargetClassNodes.get(originalTargetClassNode.name);

        ClassNode modifyMixinClassNode = mixinClassNodeHierarchy.mixinNode();
        ClassNode originalMixinClassNode = originalMixinClassNodes.get(modifyMixinClassNode.name);

        MixinTypeInfo mixinTypeInfo = determineMixinClassType(originalMixinClassNode);
        MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType = mixinTypeInfo.type();
        Annotation mixinAnnotation = mixinTypeInfo.annotation();

        TargetClassManipulator manipulator = new TargetClassManipulator(modifyTargetClassNode, originalTargetClassNode, opcodeStates);
        mixinClassType.transformBeforeEditors(mixinClassNodeHierarchy, mixinAnnotation, manipulator);

        return new PreMixinResult(originalTargetClassNode, modifyTargetClassNode, modifyMixinClassNode, originalMixinClassNode, mixinClassType, mixinAnnotation, manipulator);
    }

    private MixinResult mixin(ClassNodeHierarchy mixinClassNodeHierarchy, PreMixinResult preMixinResult) {
        ClassNode originalTargetClassNode = preMixinResult.originalTargetClassNode;
        ClassNode modifyTargetClassNode = preMixinResult.modifyTargetClassNode;

        ClassNode modifyMixinClassNode = preMixinResult.modifyMixinClassNode;
        ClassNode originalMixinClassNode = preMixinResult.originalMixinClassNode;

        MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType = preMixinResult.mixinClassType;
        Annotation mixinAnnotation = preMixinResult.mixinAnnotation;

        TargetClassManipulator manipulator = preMixinResult.manipulator;

        mapTargetSupers(modifyTargetClassNode);

        processMethodNodes(modifyMixinClassNode.methods, originalMixinClassNode, originalTargetClassNode, mixinClassType);
        processFieldNodes(modifyMixinClassNode.fields, originalMixinClassNode, originalTargetClassNode, mixinClassType);

        return new MixinResult(mixinClassType.redefineTargetFirst(), mixinClassType.transform(mixinClassNodeHierarchy, mixinAnnotation, manipulator));
    }

    private record MixinTypeInfo(MixinClassType<Annotation, ?, ?, ?, ?> type, Annotation annotation) {}

    private MixinTypeInfo determineMixinClassType(ClassNode mixinClassNode) {
        Annotation annotation;
        for (AnnotationNode annotationNode : mixinClassNode.invisibleAnnotations) {
            if (!mixinClassTypes.containsKey(annotationNode.desc)) continue;

            MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType = getMixinClassTypeInstance(annotationNode.desc);
            if (mixinClassType != null) {
                annotation = TransformerHelper.getAnnotation(annotationNode);
                return new MixinTypeInfo(mixinClassType, annotation);
            }
        }

        MixinClassType<Annotation, ?, ?, ?, ?> defaultType = (mixinClassNode.access & Opcodes.ACC_INTERFACE) == 0 ? new MixinMixinClassType() : new AccessorMixinClassType();

        return new MixinTypeInfo(defaultType, null);
    }

    private void processMethodNodes(List<MethodNode> methods, ClassNode originalMixin, ClassNode originalTarget, MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType) {
        if (methods == null) return;
        for (MethodNode method : methods) {
            if (method.invisibleAnnotations == null) {
                mixinClassType.create(method, null);
                continue;
            }
            for (AnnotationNode annotation : method.invisibleAnnotations) {
                transformNode(annotation, method, originalMixin, originalTarget, mixinClassType, true);
            }
        }
    }

    private void processFieldNodes(List<FieldNode> fields, ClassNode originalMixin, ClassNode originalTarget, MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType) {
        if (fields == null) return;
        for (FieldNode field : fields) {
            if (field.invisibleAnnotations == null) {
                mixinClassType.create(field, null);
                continue;
            }
            for (AnnotationNode annotation : field.invisibleAnnotations) {
                transformNode(annotation, field, originalMixin, originalTarget, mixinClassType, false);
            }
        }
    }

    private void transformNode(AnnotationNode annotationNode, Object node, ClassNode originalMixin, ClassNode originalTarget, MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType, boolean isMethod) {
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
            TargetMethodManipulator target = getTargetMethodEditor(builtTransformer, name, node, originalTarget, annotation);
            AnnotatedEditor annotatedEditor = createAnnotatedEditor(mixinClassType, node, target);
            TargetEditor targetEditor = mixinClassType.create(target, annotatedEditor);
            builtTransformer.transformFunction().transform(annotatedEditor, targetEditor, annotation, mixinClone, targetClone);
        } else {
            TargetFieldManipulator target = getFieldTargetEditor(builtTransformer, name, node, originalTarget, annotation);
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

    private <A extends Annotation> TargetMethodManipulator getTargetMethodEditor(BuiltTransformer builtTransformer, String name, Object node, ClassNode originalTargetClassNode, A annotation) {
        do {
            modifyTargetClassNodes.putIfAbsent(originalTargetClassNode.name, cloneClassNode(originalTargetClassNode));
            ClassNode modifyTargetClassNode = modifyTargetClassNodes.get(originalTargetClassNode.name);

            Map<String, MethodNode> modifyTargetMethodNodes = new HashMap<>();
            modifyTargetClassNode.methods.forEach(m -> modifyTargetMethodNodes.put(m.name + m.desc, m));

            for (MethodNode m : originalTargetClassNode.methods) {
                if (builtTransformer.targetFilter().isTarget(cloneNode(node), cloneNode(m), annotation))
                    return new TargetMethodManipulator(originalTargetClassNode.name, modifyTargetMethodNodes.get(m.name + m.desc), m, opcodeStates);
            }
            if(builtTransformer.allowTargetInSuper()) originalTargetClassNode = targetSuperMap.get(originalTargetClassNode.name);
            else originalTargetClassNode = null;
        } while(originalTargetClassNode != null);

        throw new MixinFormatException(name, "doesn't have a target");
    }

    private <A extends Annotation> TargetFieldManipulator getFieldTargetEditor(BuiltTransformer builtTransformer, String name, Object node, ClassNode originalTargetClassNode, A annotation) {
        do {
            modifyTargetClassNodes.putIfAbsent(originalTargetClassNode.name, cloneClassNode(originalTargetClassNode));
            ClassNode modifyTargetClassNode = modifyTargetClassNodes.get(originalTargetClassNode.name);

            Map<String, FieldNode> modifyFieldNodes = new HashMap<>();
            modifyTargetClassNode.fields.forEach(f -> modifyFieldNodes.put(f.name, f));

            for(FieldNode f : originalTargetClassNode.fields) {
                if(builtTransformer.targetFilter().isTarget(cloneNode(node), cloneNode(f), annotation))
                    return new TargetFieldManipulator(modifyFieldNodes.get(f.name), f);
            }
            if(builtTransformer.allowTargetInSuper()) originalTargetClassNode = targetSuperMap.get(originalTargetClassNode.name);
            else originalTargetClassNode = null;
        } while(originalTargetClassNode != null);

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

    private record PreMixinResult(ClassNode originalTargetClassNode, ClassNode modifyTargetClassNode, ClassNode modifyMixinClassNode, ClassNode originalMixinClassNode, MixinClassType<Annotation, ?, ?, ?, ?> mixinClassType, Annotation mixinAnnotation, TargetClassManipulator manipulator) {}
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