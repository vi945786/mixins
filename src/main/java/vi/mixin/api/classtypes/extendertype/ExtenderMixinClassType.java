package vi.mixin.api.classtypes.extendertype;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.classtypes.ClassNodeHierarchy;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.util.TransformerHelper;
import vi.mixin.api.classtypes.targeteditors.TargetClassManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetFieldManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetMethodManipulator;

import java.util.HashMap;
import java.util.Map;

public class ExtenderMixinClassType implements MixinClassType<Extends, ExtenderAnnotatedMethodEditor, ExtenderAnnotatedFieldEditor, ExtenderTargetMethodEditor, ExtenderTargetFieldEditor> {

    private final Map<MethodNode, ExtenderAnnotatedMethodEditor> annotatedMethodEditors = new HashMap<>();
    private final Map<FieldNode, ExtenderAnnotatedFieldEditor> annotatedFieldEditors = new HashMap<>();

    @Override
    public ExtenderAnnotatedMethodEditor create(MethodNode annotatedMethodNode, Object targetEditor) {
        ExtenderAnnotatedMethodEditor editor = new ExtenderAnnotatedMethodEditor(annotatedMethodNode, targetEditor);
        annotatedMethodEditors.put(annotatedMethodNode, editor);
        return editor;
    }

    @Override
    public ExtenderAnnotatedFieldEditor create(FieldNode annotatedFieldNode, Object targetEditor) {
        ExtenderAnnotatedFieldEditor editor = new ExtenderAnnotatedFieldEditor(annotatedFieldNode, targetEditor);
        annotatedFieldEditors.put(annotatedFieldNode, editor);
        return editor;
    }

    @Override
    public ExtenderTargetMethodEditor create(TargetMethodManipulator targetMethodEditors, Object mixinEditor) {
        return new ExtenderTargetMethodEditor(targetMethodEditors, mixinEditor);
    }

    @Override
    public ExtenderTargetFieldEditor create(TargetFieldManipulator targetFieldEditors, Object mixinEditor) {
        return new ExtenderTargetFieldEditor(targetFieldEditors, mixinEditor);
    }

    private static void validateClass(ClassNodeHierarchy mixinClassNodeHierarchy, TargetClassManipulator targetClassEditor) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.mixinNode();

        String name = "@Extends " + mixinClassNode.name;
        if(mixinClassNode.superName != null && !mixinClassNode.superName.equals("java/lang/Object")) throw new MixinFormatException(name, "extends " + mixinClassNode.superName + ". @Extend classes must not extend any other class");

        if((targetClassEditor.getClassNodeClone().access & ACC_INTERFACE) != 0) throw new MixinFormatException(name, "the target is an interface while the mixin is not.");

        if(targetClassEditor.getMethodManipulator("<init>()V") == null) {
            for (MethodNode methodNode : mixinClassNode.methods) {
                if (!methodNode.name.equals("<init>")) continue;
                boolean foundCall = false;
                for(AbstractInsnNode node : methodNode.instructions) {
                    if (!(node instanceof MethodInsnNode methodInsnNode)) continue;
                    if(!methodInsnNode.owner.equals(mixinClassNode.name) || !methodNode.name.equals("<init>")) {
                        MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                        if(nodeMethodNode == null || nodeMethodNode.invisibleAnnotations == null || nodeMethodNode.invisibleAnnotations.stream().noneMatch(a -> a.desc.equals(Type.getDescriptor(New.class)))) continue;
                    }

                    foundCall = true;
                    break;
                }
                if(!foundCall) throw new MixinFormatException(name, "constructor doesn't have call to super class constructor using @New");
            }
        }
        for (ClassNode classNode : mixinClassNodeHierarchy.getAllMixinClassesInHierarchy()) {
            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.name.equals("<init>")) continue;
                for (AbstractInsnNode node : methodNode.instructions) {
                    if (!(node instanceof MethodInsnNode methodInsnNode)) continue;
                    MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                    if(nodeMethodNode == null || nodeMethodNode.invisibleAnnotations == null || nodeMethodNode.invisibleAnnotations.stream().noneMatch(a -> a.desc.equals(Type.getDescriptor(New.class)))) continue;
                    throw new MixinFormatException(name, "@New calls in @Extends classes are only allowed in the constructors of the mixin");
                }
            }
        }
    }

    private static void validateInterface(ClassNodeHierarchy mixinClassNodeHierarchy, TargetClassManipulator targetClassEditor) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.mixinNode();

        String name = "@Extends " + mixinClassNode.name;
        if ((targetClassEditor.getClassNodeClone().access & ACC_INTERFACE) == 0) throw new MixinFormatException(name, "the mixin is an interface while the target is not.");
    }

    @Override
    public void transformBeforeEditors(ClassNodeHierarchy mixinClassNodeHierarchy, Extends annotation, TargetClassManipulator targetClassManipulator) {
        if((mixinClassNodeHierarchy.mixinNode().access & ACC_INTERFACE) == 0) transformClass(mixinClassNodeHierarchy, targetClassManipulator);
        else transformInterface(mixinClassNodeHierarchy, targetClassManipulator);
    }

    @Override
    public String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Extends annotation, TargetClassManipulator targetClassManipulator) {
        if((mixinClassNodeHierarchy.mixinNode().access & ACC_INTERFACE) != 0) return null;

        ClassNode mixinClassNode = mixinClassNodeHierarchy.mixinNode();
        ClassNode targetClassNode = targetClassManipulator.getClassNodeClone();

        for (ClassNode classNode : mixinClassNodeHierarchy.getAllMixinClassesInHierarchy()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if(insnNode instanceof MethodInsnNode methodInsnNode && methodInsnNode.owner.equals(mixinClassNode.name)) {
                        MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                        if(nodeMethodNode != null && annotatedMethodEditors.get(nodeMethodNode) != null && annotatedMethodEditors.get(nodeMethodNode).invoke != null) {
                            methodNode.instructions.insertBefore(insnNode, annotatedMethodEditors.get(nodeMethodNode).invoke.clone(null));
                            methodNode.instructions.remove(methodInsnNode);
                        }
                    }
                    if(insnNode instanceof FieldInsnNode fieldInsnNode) {
                        FieldNode nodeFieldNode = mixinClassNode.fields.stream().filter(m -> m.name.equals(fieldInsnNode.name)).findAny().orElse(null);
                        if(nodeFieldNode != null && (nodeFieldNode.access & ACC_SYNTHETIC) == 0) {
                            FieldInsnNode change;
                            if (fieldInsnNode.getOpcode() == PUTFIELD || fieldInsnNode.getOpcode() == PUTSTATIC) change = annotatedFieldEditors.get(nodeFieldNode).set;
                            else change = annotatedFieldEditors.get(nodeFieldNode).get;

                            if(change == null) continue;
                            methodNode.instructions.insertBefore(insnNode, change.clone(null));
                            methodNode.instructions.remove(fieldInsnNode);
                        }
                    }
                }
            }
        }

        for (MethodNode methodNode : mixinClassNode.methods) {
            if (!methodNode.name.equals("<init>")) continue;

            boolean switched = false;
            for (AbstractInsnNode node : methodNode.instructions) {
                if (!(node instanceof MethodInsnNode methodInsnNode)) continue;
                MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                if(nodeMethodNode == null || nodeMethodNode.invisibleAnnotations == null) continue;

                AnnotationNode newAnnotation = nodeMethodNode.invisibleAnnotations.stream().filter(a -> a.desc.equals(Type.getDescriptor(New.class))).findAny().orElse(null);
                if(newAnnotation == null) continue;

                methodNode.instructions.insertBefore(methodInsnNode, new MethodInsnNode(INVOKESPECIAL, targetClassNode.name, "<init>", "(" + newAnnotation.values.get(1) + ")V"));
                methodNode.instructions.remove(methodInsnNode);
                switched = true;
            }
            for (AbstractInsnNode node : TransformerHelper.getInsnNodes(methodNode.instructions, AbstractInsnNode.METHOD_INSN, INVOKESPECIAL, null, null, "<init>", "()V")) {
                if(targetClassManipulator.getMethodManipulator("<init>()V") != null && !switched) methodNode.instructions.insertBefore(node, new MethodInsnNode(INVOKESPECIAL, targetClassNode.name, "<init>", "()V"));
                methodNode.instructions.remove(node);
            }
        }

        annotatedFieldEditors.forEach((fieldNode, fieldEditor) -> {
            if(fieldEditor == null) return;

            if(fieldEditor.delete) {
                mixinClassNode.fields.remove(fieldNode);
            }
        });

        annotatedMethodEditors.forEach((methodNode, methodEditor) -> {
            if(methodNode == null) return;

            if(methodEditor.delete) {
                mixinClassNode.methods.remove(methodNode);
            }
        });

        return null;
    }

    public void transformClass(ClassNodeHierarchy mixinClassNodeHierarchy, TargetClassManipulator targetClassEditor) {
        validateClass(mixinClassNodeHierarchy, targetClassEditor);
        ClassNode mixinClassNode = mixinClassNodeHierarchy.mixinNode();
        ClassNode targetClassNode = targetClassEditor.getClassNodeClone();

        targetClassEditor.makePublic();
        targetClassEditor.makeNonFinalOrSealed();

        mixinClassNode.superName = targetClassNode.name;
    }

    public void transformInterface(ClassNodeHierarchy mixinClassNodeHierarchy, TargetClassManipulator targetClassEditor) {
        validateInterface(mixinClassNodeHierarchy, targetClassEditor);
        ClassNode mixinClassNode = mixinClassNodeHierarchy.mixinNode();
        ClassNode targetClassNode = targetClassEditor.getClassNodeClone();

        targetClassEditor.makePublic();
        targetClassEditor.makeNonFinalOrSealed();

        mixinClassNode.interfaces.add(targetClassNode.name);
    }
}
