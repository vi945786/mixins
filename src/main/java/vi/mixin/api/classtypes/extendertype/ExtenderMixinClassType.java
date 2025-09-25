package vi.mixin.api.classtypes.extendertype;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.classtypes.ClassNodeHierarchy;
import vi.mixin.api.classtypes.Editors;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.util.TransformerHelper;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetClassEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;


public class ExtenderMixinClassType implements MixinClassType<Extends, ExtenderAnnotatedMethodEditor, ExtenderAnnotatedFieldEditor, ExtenderTargetMethodEditor, ExtenderTargetFieldEditor> {

    @Override
    public ExtenderAnnotatedMethodEditor create(MethodNode mixinMethodNode, Object targetEditors) {
        return new ExtenderAnnotatedMethodEditor(mixinMethodNode, targetEditors);
    }

    @Override
    public ExtenderAnnotatedFieldEditor create(FieldNode mixinFieldNode, Object targetEditors) {
        return new ExtenderAnnotatedFieldEditor(mixinFieldNode, targetEditors);
    }

    @Override
    public ExtenderTargetMethodEditor create(MixinClassTargetMethodEditor targetMethodEditors, Object mixinEditor) {
        return new ExtenderTargetMethodEditor(targetMethodEditors, mixinEditor);
    }

    @Override
    public ExtenderTargetFieldEditor create(MixinClassTargetFieldEditor targetFieldEditors, Object mixinEditor) {
        return new ExtenderTargetFieldEditor(targetFieldEditors, mixinEditor);
    }

    private static void validate(ClassNodeHierarchy mixinClassNodeHierarchy, MixinClassTargetClassEditor targetClassEditor) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.classNode();

        String name = "@Extends " + mixinClassNode.name;
        if(mixinClassNode.superName != null && !mixinClassNode.superName.equals("java/lang/Object")) throw new MixinFormatException(name, "extends " + mixinClassNode.superName + ". @Extend classes must not extend any other class");
        if((mixinClassNode.access & ACC_INTERFACE) != 0) throw new MixinFormatException(name, "@Extends is not allowed on interfaces");

        if(targetClassEditor.getMethodEditor("<init>()V") == null) {
            for (MethodNode methodNode : mixinClassNode.methods) {
                if (!methodNode.name.equals("<init>")) continue;
                boolean foundCall = false;
                for(AbstractInsnNode node : methodNode.instructions) {
                    if (!(node instanceof MethodInsnNode methodInsnNode)) continue;
                    MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                    if(nodeMethodNode == null || nodeMethodNode.invisibleAnnotations == null || nodeMethodNode.invisibleAnnotations.stream().noneMatch(a -> a.desc.equals(Type.getDescriptor(New.class)))) continue;

                    foundCall = true;
                    break;
                }
                if(!foundCall) throw new MixinFormatException(name, "constructor doesn't have call to super class constructor using @New");
            }
        }
        for (ClassNode classNode : mixinClassNodeHierarchy.getAllClassesInHierarchy()) {
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


    @Override
    public String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Editors<ExtenderAnnotatedMethodEditor, ExtenderAnnotatedFieldEditor, ExtenderTargetMethodEditor, ExtenderTargetFieldEditor> editors, Extends annotation, MixinClassTargetClassEditor targetClassEditor) {
        validate(mixinClassNodeHierarchy, targetClassEditor);
        ClassNode mixinClassNode = mixinClassNodeHierarchy.classNode();
        ClassNode targetClassNode = targetClassEditor.getClassNodeClone();

        targetClassEditor.makePublic();
        targetClassEditor.makeNonFinalOrSealed();

        mixinClassNode.superName = targetClassNode.name;

        for (ClassNode classNode : mixinClassNodeHierarchy.getAllClassesInHierarchy()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if(insnNode instanceof MethodInsnNode methodInsnNode && methodInsnNode.owner.equals(mixinClassNode.name)) {
                        MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                        if(nodeMethodNode != null && editors.mixinMethodEditors().get(nodeMethodNode) != null && editors.mixinMethodEditors().get(nodeMethodNode).invoke != null) {
                            methodNode.instructions.insertBefore(insnNode, editors.mixinMethodEditors().get(nodeMethodNode).invoke);
                            methodNode.instructions.remove(methodInsnNode);
                        }
                    }
                    if(insnNode instanceof FieldInsnNode fieldInsnNode) {
                        FieldNode nodeFieldNode = mixinClassNode.fields.stream().filter(m -> m.name.equals(fieldInsnNode.name)).findAny().orElse(null);
                        if(nodeFieldNode != null && (nodeFieldNode.access & ACC_SYNTHETIC) == 0) {
                            FieldInsnNode change;
                            if (fieldInsnNode.getOpcode() == PUTFIELD || fieldInsnNode.getOpcode() == PUTSTATIC) change = editors.mixinFieldEditors().get(nodeFieldNode).set;
                            else change = editors.mixinFieldEditors().get(nodeFieldNode).get;

                            if(change == null) continue;
                            methodNode.instructions.insertBefore(insnNode, change);
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
                if(targetClassEditor.getMethodEditor("<init>()V") != null && !switched) methodNode.instructions.insertBefore(node, new MethodInsnNode(INVOKESPECIAL, targetClassNode.name, "<init>", "()V"));
                methodNode.instructions.remove(node);
            }
        }

        editors.mixinFieldEditors().forEach((fieldNode, fieldEditor) -> {
            if(fieldEditor == null) return;

            if(fieldEditor.delete) {
                mixinClassNode.fields.remove(fieldNode);
            }
        });

        editors.mixinMethodEditors().forEach((methodNode, methodEditor) -> {
            if(methodNode == null) return;

            if(methodEditor.delete) {
                mixinClassNode.methods.remove(methodNode);
            }
        });

        return null;
    }
}
