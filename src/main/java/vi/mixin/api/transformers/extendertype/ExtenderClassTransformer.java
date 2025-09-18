package vi.mixin.api.transformers.extendertype;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.transformers.ClassNodeHierarchy;
import vi.mixin.api.transformers.ClassTransformer;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.targeteditors.TargetClassEditor;
import vi.mixin.api.transformers.targeteditors.TargetFieldEditor;
import vi.mixin.api.transformers.targeteditors.TargetMethodEditor;

import java.util.Map;

public class ExtenderClassTransformer implements ClassTransformer<Extends, ExtenderMethodTransformer, ExtenderFieldTransformer, ExtenderMethodEditor, ExtenderFieldEditor> {

    @Override
    public ExtenderMethodEditor create(MethodNode mixinMethodNode, TargetMethodEditor[] targetMethodEditors) {
        return new ExtenderMethodEditor(mixinMethodNode, targetMethodEditors);
    }

    @Override
    public ExtenderMethodEditor create(FieldNode mixinFieldNode, TargetMethodEditor[] targetMethodEditors) {
        return new ExtenderMethodEditor(mixinFieldNode, targetMethodEditors);
    }

    @Override
    public ExtenderFieldEditor create(MethodNode mixinMethodNode, TargetFieldEditor[] targetFieldEditors) {
        return new ExtenderFieldEditor(mixinMethodNode, targetFieldEditors);
    }

    @Override
    public ExtenderFieldEditor create(FieldNode mixinFieldNode, TargetFieldEditor[] targetFieldEditors) {
        return new ExtenderFieldEditor(mixinFieldNode, targetFieldEditors);
    }

    @Override
    public Class<ExtenderMethodTransformer> getMethodTransformerType() {
        return ExtenderMethodTransformer.class;
    }

    @Override
    public Class<ExtenderFieldTransformer> getFieldTransformerType() {
        return ExtenderFieldTransformer.class;
    }

    private static void validate(ClassNodeHierarchy mixinClassNodeHierarchy, Map<MethodNode, ExtenderMethodEditor> methodNodeEditorMap, Map<FieldNode, ExtenderFieldEditor> fieldNodeEditorMap, Extends annotation, TargetClassEditor targetClassEditor) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.getClassNode();

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
    public String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Map<MethodNode, ExtenderMethodEditor> methodNodeEditorMap, Map<FieldNode, ExtenderFieldEditor> fieldNodeEditorMap, Extends annotation, TargetClassEditor targetClassEditor) {
        validate(mixinClassNodeHierarchy, methodNodeEditorMap, fieldNodeEditorMap, annotation, targetClassEditor);
        ClassNode mixinClassNode = mixinClassNodeHierarchy.getClassNode();
        ClassNode targetClassNode = targetClassEditor.getClassNodeClone();

        targetClassEditor.makePublic();
        targetClassEditor.makeNonFinalOrSealed();

        mixinClassNode.superName = targetClassNode.name;

        for (ClassNode classNode : mixinClassNodeHierarchy.getAllClassesInHierarchy()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if(insnNode instanceof MethodInsnNode methodInsnNode && methodInsnNode.owner.equals(mixinClassNode.name)) {
                        MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                        if(nodeMethodNode != null && methodNodeEditorMap.get(nodeMethodNode) != null && methodNodeEditorMap.get(nodeMethodNode).invoke != null) {
                            methodNode.instructions.insertBefore(insnNode, methodNodeEditorMap.get(nodeMethodNode).invoke);
                            methodNode.instructions.remove(methodInsnNode);
                        }
                    }
                    if(insnNode instanceof FieldInsnNode fieldInsnNode) {
                        FieldNode nodeFieldNode = mixinClassNode.fields.stream().filter(m -> m.name.equals(fieldInsnNode.name)).findAny().orElse(null);
                        if(nodeFieldNode != null && (nodeFieldNode.access & ACC_SYNTHETIC) == 0) {
                            FieldInsnNode change;
                            if (fieldInsnNode.getOpcode() == PUTFIELD || fieldInsnNode.getOpcode() == PUTSTATIC) change = fieldNodeEditorMap.get(nodeFieldNode).set;
                            else change = fieldNodeEditorMap.get(nodeFieldNode).get;

                            if(change == null) continue;
                            methodNode.instructions.insertBefore(insnNode, change);
                            methodNode.instructions.remove(fieldInsnNode);
                        }
                    }
                }
            }
        }

        fieldNodeEditorMap.forEach((fieldNode, fieldEditor) -> {
            if(fieldEditor == null) return;

            if(fieldEditor.delete) {
                mixinClassNode.fields.remove(fieldNode);
            }
        });

        methodNodeEditorMap.forEach((methodNode, methodEditor) -> {
            if(methodNode == null) return;

            if(methodEditor.delete) {
                mixinClassNode.methods.remove(methodNode);
            }
        });

        for (MethodNode methodNode : mixinClassNode.methods) {
            if (!methodNode.name.equals("<init>")) continue;

            boolean switched = false;
            for (AbstractInsnNode node : methodNode.instructions) {
                if (!(node instanceof MethodInsnNode methodInsnNode)) continue;
                MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                if(nodeMethodNode == null || nodeMethodNode.invisibleAnnotations == null || nodeMethodNode.invisibleAnnotations.stream().noneMatch(a -> a.desc.equals(Type.getDescriptor(New.class)))) continue;

                String newDesc = methodInsnNode.desc;
                newDesc = newDesc.split("\\)")[0] + ")V";

                methodNode.instructions.insertBefore(methodInsnNode, new MethodInsnNode(INVOKESPECIAL, targetClassNode.name, "<init>", newDesc));
                methodNode.instructions.remove(methodInsnNode);
                switched = true;
            }
            for (AbstractInsnNode node : TransformerHelper.getInsnNodes(methodNode.instructions, AbstractInsnNode.METHOD_INSN, INVOKESPECIAL, null, null, "<init>", "()V")) {
                if(targetClassEditor.getMethodEditor("<init>()V") != null && !switched) methodNode.instructions.insertBefore(node, new MethodInsnNode(INVOKESPECIAL, targetClassNode.name, "<init>", "()V"));
                methodNode.instructions.remove(node);
            }
        }

        return null;
    }
}
