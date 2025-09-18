package vi.mixin.api.transformers.mixintype;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.transformers.*;
import vi.mixin.api.transformers.targeteditors.TargetClassEditor;
import vi.mixin.api.transformers.targeteditors.TargetFieldEditor;
import vi.mixin.api.transformers.targeteditors.TargetInsnListEditor;
import vi.mixin.api.transformers.targeteditors.TargetMethodEditor;

import java.util.Map;

import static vi.mixin.api.transformers.TransformerHelper.addLoadOpcodesOfMethod;
import static vi.mixin.api.transformers.TransformerHelper.getOuterClassInstanceFieldName;

public final class MixinClassTransformer implements ClassTransformer<Mixin, MixinMethodTransformer, MixinFieldTransformer, MixinMethodEditor, MixinFieldEditor> {

    @Override
    public MixinMethodEditor create(MethodNode mixinMethodNode, TargetMethodEditor[] targetMethodEditors) {
        return new MixinMethodEditor(mixinMethodNode, targetMethodEditors);
    }

    @Override
    public MixinMethodEditor create(FieldNode mixinFieldNode, TargetMethodEditor[] targetMethodEditors) {
        return new MixinMethodEditor(mixinFieldNode, targetMethodEditors);
    }

    @Override
    public MixinFieldEditor create(MethodNode mixinMethodNode, TargetFieldEditor[] targetFieldEditors) {
        return new MixinFieldEditor(mixinMethodNode, targetFieldEditors);
    }

    @Override
    public MixinFieldEditor create(FieldNode mixinFieldNode, TargetFieldEditor[] targetFieldEditors) {
        return new MixinFieldEditor(mixinFieldNode, targetFieldEditors);
    }

    @Override
    public Class<MixinMethodTransformer> getMethodTransformerType() {
        return MixinMethodTransformer.class;
    }

    @Override
    public Class<MixinFieldTransformer> getFieldTransformerType() {
        return MixinFieldTransformer.class;
    }

    private ClassNodeHierarchy mixinClassNodeHierarchy;
    private ClassNode mixinClassNode;
    private TargetClassEditor targetClassEditor;
    private ClassNode targetClassNode;
    private Class<?> targetClass;
    private String replaceName;

    @Override
    public String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Map<MethodNode, MixinMethodEditor> methodNodeEditorMap, Map<FieldNode, MixinFieldEditor> fieldNodeEditorMap, Mixin annotation, TargetClassEditor targetClassEditor) {
        this.mixinClassNodeHierarchy = mixinClassNodeHierarchy;
        this.mixinClassNode = mixinClassNodeHierarchy.getClassNode();
        this.targetClassEditor = targetClassEditor;
        this.targetClassNode = targetClassEditor.getClassNodeClone();
        this.targetClass = targetClassEditor.getRealClass();
        replaceName = mixinClassNode.name.replace("/", "$$") + "$$";
        validate();

        String targetOuterInstanceFieldName = getOuterClassInstanceFieldName(targetClassNode, targetClass);
        if(mixinClassNodeHierarchy.getParent() != null) addOuterClassInstanceFieldToTarget(targetOuterInstanceFieldName);

        //add init to target class for default field values
        String clinitName = moveClinit();
        addAndMoveInit();
        addExceptionToConstructor();

        for (ClassNode classNode : mixinClassNodeHierarchy.getAllClassesInHierarchy()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if(insnNode instanceof TypeInsnNode typeInsnNode && typeInsnNode.getOpcode() == CHECKCAST) {
                        if(typeInsnNode.desc.equals(mixinClassNode.name) || typeInsnNode.desc.equals(targetClassNode.name)) {
                            methodNode.instructions.remove(typeInsnNode);
                        }
                    }
                    //replace mixin outer class instance field with the target outer class instance field
                    if (mixinClassNodeHierarchy.getParent() != null && (methodNode.access & ACC_STATIC) == 0) {
                        for (AbstractInsnNode node : TransformerHelper.getInsnNodes(methodNode.instructions, AbstractInsnNode.FIELD_INSN, GETFIELD, mixinClassNode.name, getOuterClassInstanceFieldName(mixinClassNode, targetClass), "L" + mixinClassNodeHierarchy.getParent().getClassNode().name + ";")) {
                            FieldInsnNode fieldInsnNode = (FieldInsnNode) node;

                            String type = Type.getType(TransformerHelper.getTargetClass(mixinClassNode)).getInternalName();
                            String outerType = Type.getType(TransformerHelper.getTargetClass(mixinClassNodeHierarchy.getParent().getClassNode())).getDescriptor();
                            methodNode.instructions.insertBefore(fieldInsnNode, new FieldInsnNode(GETFIELD, type, fieldInsnNode.name, outerType));
                            methodNode.instructions.remove(fieldInsnNode);
                        }
                    }
                    if(insnNode instanceof MethodInsnNode methodInsnNode && methodInsnNode.owner.equals(mixinClassNode.name)) {
                        MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                        if(nodeMethodNode != null) {
                            MethodInsnNode invoke = methodNodeEditorMap.get(nodeMethodNode).invoke;
                            if(invoke == null) invoke = new MethodInsnNode(methodInsnNode.getOpcode(), targetClassNode.name, replaceName + methodInsnNode.name, methodInsnNode.desc);

                            methodNode.instructions.insertBefore(insnNode, invoke);
                            methodNode.instructions.remove(methodInsnNode);
                        }
                    }
                    if(insnNode instanceof FieldInsnNode fieldInsnNode) {
                        FieldNode nodeFieldNode = mixinClassNode.fields.stream().filter(m -> m.name.equals(fieldInsnNode.name)).findAny().orElse(null);
                        if(nodeFieldNode != null && (nodeFieldNode.access & ACC_SYNTHETIC) == 0) {
                            FieldInsnNode change;
                            if (fieldInsnNode.getOpcode() == PUTFIELD || fieldInsnNode.getOpcode() == PUTSTATIC) change = fieldNodeEditorMap.get(nodeFieldNode).set;
                            else change = fieldNodeEditorMap.get(nodeFieldNode).get;
                            if(change == null) change = new FieldInsnNode(fieldInsnNode.getOpcode(), targetClassNode.name, replaceName + fieldInsnNode.name, fieldInsnNode.desc);
                            methodNode.instructions.insertBefore(insnNode, change);
                            methodNode.instructions.remove(fieldInsnNode);
                        }
                    }
                }
            }
        }

        //move fields to target class
        fieldNodeEditorMap.forEach((fieldNode, fieldEditor) -> {
            if(fieldNode == null) return;

            if(fieldEditor.copy) {
                targetClassEditor.addField(new FieldNode((fieldNode.access & ~(ACC_PRIVATE | ACC_PROTECTED)) | ACC_PUBLIC, replaceName + fieldNode.name, fieldNode.desc, fieldNode.signature, fieldNode.value));
                mixinClassNode.fields.remove(fieldNode);
            }
            mixinClassNode.fields.remove(fieldNode);
        });

        methodNodeEditorMap.forEach((methodNode, methodEditor) -> {
            if(methodNode == null || methodNode.name.startsWith("<")) return;

            if(methodEditor.delete) {
                mixinClassNode.methods.remove(methodNode);
                return;
            }

            if(methodEditor.copy) {
                //make methods acts like instance methods of target
                methodNode.access &= ~ACC_PRIVATE;
                methodNode.access &= ~ACC_PROTECTED;
                methodNode.access |= ACC_PUBLIC;

                //add invokers in target class
                methodNode.access &= ~ACC_ABSTRACT;
                MethodNode addMethodNode = new MethodNode(methodNode.access, replaceName + methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(String[]::new));
                targetClassEditor.addMethod(addMethodNode);
                boolean isStatic = (addMethodNode.access & ACC_STATIC) != 0;
                if ((methodNode.access & ACC_STATIC) == 0) methodNode.desc = getNewDesc(methodNode.desc, targetClassNode.name);
                methodNode.access |= ACC_STATIC;

                int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(addMethodNode.desc));

                addLoadOpcodesOfMethod(addMethodNode.instructions, Type.getArgumentTypes(addMethodNode.desc), isStatic);
                addMethodNode.instructions.add(new MethodInsnNode(INVOKESTATIC, mixinClassNode.name, methodNode.name, methodNode.desc));
                addMethodNode.instructions.add(new InsnNode(returnOpcode));
            }
        });

        return clinitName;
    }

    static String getNewDesc(String desc, String targetClassNodeName) {
        return "(L" + targetClassNodeName + ";" + desc.substring(1);
    }

    private void validate() {
        if (mixinClassNode.methods.stream().filter(m -> m.name.equals("<init>")).count() == 2) throw new MixinFormatException(mixinClassNode.name, "has 2 constructors");
    }

    private void addOuterClassInstanceFieldToTarget(String targetOuterFieldName) {
        Type outerType = Type.getType(targetClass.getDeclaringClass());

        FieldNode targetOuterFieldNode = targetClassNode.fields.stream().filter(f -> f.name.equals(targetOuterFieldName)).findAny().orElse(null);
        if(targetOuterFieldNode == null) {
            targetOuterFieldNode = new FieldNode(Opcodes.ACC_PUBLIC, targetOuterFieldName, outerType.getDescriptor(), null, null);
            targetClassEditor.addField(targetOuterFieldNode);

            targetClassEditor.getMethodEditors().stream().filter(methodNode -> methodNode.getMethodNodeClone().name.equals("<init>")).forEach(methodEditor -> {
                InsnList insnList = new InsnList();
                insnList.add(new VarInsnNode(ALOAD, 0));
                insnList.add(new VarInsnNode(ALOAD, 1));
                insnList.add(new FieldInsnNode(PUTFIELD, targetClassNode.name, targetOuterFieldName, outerType.getDescriptor()));
                methodEditor.getInsnListEditor().insertBefore(0, insnList);
            });
        } else {
            targetClassEditor.getFieldEditor(targetOuterFieldNode.name).makeNonSynthetic();
            targetOuterFieldNode.access = targetOuterFieldNode.access & ~Opcodes.ACC_SYNTHETIC & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;
        }
    }

    private String moveClinit() {
        MethodNode mixinClinit = mixinClassNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findAny().orElse(null);
        if(mixinClinit == null) return null;

        MethodNode newClinit = new MethodNode(ACC_PUBLIC | ACC_STATIC, replaceName + "clinit", "()V", null, null);
        mixinClassNode.methods.add(newClinit);
        newClinit.instructions = mixinClinit.instructions;

        mixinClassNode.methods.remove(mixinClinit);
        return newClinit.name;
    }

    private void addAndMoveInit() {
        MethodNode mixinInit = mixinClassNode.methods.stream().filter(m -> m.name.equals("<init>")).findAny().orElse(null);
        MethodNode newInit = new MethodNode(ACC_PUBLIC | ACC_STATIC, replaceName + "init", "(L" + targetClassNode.name + ";)V", null, mixinInit.exceptions.toArray(String[]::new));
        mixinClassNode.methods.add(newInit);

        String outerClassInstanceFieldName = getOuterClassInstanceFieldName(mixinClassNode, targetClass);
        FieldNode outerClassInstanceField = mixinClassNode.fields.stream().filter(f -> f.name.equals(outerClassInstanceFieldName)).findAny().orElse(null);
        if(outerClassInstanceField != null) {
            mixinClassNode.fields.remove(outerClassInstanceField);
            mixinInit.instructions.remove(mixinInit.instructions.get(2));
            mixinInit.instructions.remove(mixinInit.instructions.get(2));
            mixinInit.instructions.remove(mixinInit.instructions.get(2));
            mixinClassNode.access |= ACC_STATIC;
        }

        boolean sawNew = false;
        boolean afterSuper = false;
        for (AbstractInsnNode node : mixinInit.instructions) {
            if (afterSuper) {
                mixinInit.instructions.remove(node);
                newInit.instructions.add(node);
                continue;
            }
            if (node.getOpcode() == NEW) sawNew = true;
            if (node.getOpcode() == INVOKESPECIAL && node instanceof MethodInsnNode methodInsnNode && methodInsnNode.name.equals("<init>")) {
                if (sawNew) sawNew = false;
                else afterSuper = true;
            }
        }
        mixinInit.instructions.add(new InsnNode(RETURN));

        targetClassEditor.getMethodEditors().stream().filter(methodNode -> methodNode.getMethodNodeClone().name.equals("<init>")).forEach(methodEditor -> {
            TargetInsnListEditor insnListEditor = methodEditor.getInsnListEditor();
            for (int index : TransformerHelper.getInsnNodesIndexes(methodEditor.getInsnListEditor().getInsnListClone(), AbstractInsnNode.INSN, RETURN)) {
                InsnList insnList = new InsnList();
                insnList.add(new VarInsnNode(ALOAD, 0));
                insnList.add(new MethodInsnNode(INVOKESTATIC, mixinClassNode.name, newInit.name, newInit.desc));
                insnListEditor.insertBefore(index, insnList);
            }
        });
    }

    private void addExceptionToConstructor() {
        mixinClassNode.methods.stream().filter(m -> m.name.equals("<init>")).forEach(methodNode -> {
            InsnList insnNodes = methodNode.instructions;
            for (int i = 0; i < insnNodes.size(); i++) {
                AbstractInsnNode insnNode = insnNodes.get(i);
                if (insnNode.getOpcode() != RETURN) continue;

                insnNodes.insertBefore(insnNode, new TypeInsnNode(NEW, Type.getType(UnsupportedOperationException.class).getInternalName()));
                insnNodes.insertBefore(insnNode, new InsnNode(DUP));
                insnNodes.insertBefore(insnNode, new LdcInsnNode("attempted to invoke " + mixinClassNode.name + "." + methodNode.name + methodNode.desc));
                insnNodes.insertBefore(insnNode, new MethodInsnNode(INVOKESPECIAL, Type.getType(UnsupportedOperationException.class).getInternalName(), "<init>", "(Ljava/lang/String;)V"));
                insnNodes.insertBefore(insnNode, new InsnNode(ATHROW));
                break;
            }
        });
    }
}
