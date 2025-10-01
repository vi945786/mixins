package vi.mixin.api.classtypes.mixintype;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.classtypes.ClassNodeHierarchy;
import vi.mixin.api.classtypes.Editors;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.util.TransformerHelper;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetClassEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetInsnListEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;
import vi.mixin.bytecode.LambdaHandler;

import java.util.Arrays;

import static vi.mixin.api.util.TransformerHelper.*;

public class MixinMixinClassType implements MixinClassType<Mixin, MixinAnnotatedMethodEditor, MixinAnnotatedFieldEditor, MixinTargetMethodEditor, MixinTargetFieldEditor> {

    @Override
    public MixinAnnotatedMethodEditor create(MethodNode mixinMethodNode, Object targetEditors) {
        return new MixinAnnotatedMethodEditor(mixinMethodNode, targetEditors);
    }

    @Override
    public MixinAnnotatedFieldEditor create(FieldNode mixinFieldNode, Object targetEditors) {
        return new MixinAnnotatedFieldEditor(mixinFieldNode, targetEditors);
    }

    @Override
    public MixinTargetMethodEditor create(MixinClassTargetMethodEditor targetMethodEditors, Object mixinEditors) {
        return new MixinTargetMethodEditor(targetMethodEditors, mixinEditors);
    }

    @Override
    public MixinTargetFieldEditor create(MixinClassTargetFieldEditor targetFieldEditors, Object mixinEditors) {
        return new MixinTargetFieldEditor(targetFieldEditors, mixinEditors);
    }

    private ClassNode mixinClassNode;
    private MixinClassTargetClassEditor targetClassEditor;
    private ClassNode targetClassNode;
    private Class<?> targetClass;
    private String replaceName;

    @Override
    public String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Editors<MixinAnnotatedMethodEditor, MixinAnnotatedFieldEditor, MixinTargetMethodEditor, MixinTargetFieldEditor> editors, Mixin annotation, MixinClassTargetClassEditor targetClassEditor) {
        this.mixinClassNode = mixinClassNodeHierarchy.classNode();
        this.targetClassEditor = targetClassEditor;
        this.targetClassNode = targetClassEditor.getClassNodeClone();
        this.targetClass = targetClassEditor.getRealClass();
        replaceName = mixinClassNode.name.replace("/", "$$") + "$$";
        validate();

        mixinClassNode.access |= ACC_PUBLIC;
        mixinClassNode.access &= ~ACC_PRIVATE;
        mixinClassNode.access &= ~ACC_PROTECTED;

        String targetOuterInstanceFieldName = getOuterClassInstanceFieldName(targetClassNode, targetClass);
        if(mixinClassNodeHierarchy.parent() != null) addOuterClassInstanceFieldToTarget(targetOuterInstanceFieldName);

        //add init to target class for default field values
        String clinitName = moveClinit();
        addAndMoveInit();
        addExceptionToConstructor();

        for (ClassNode classNode : mixinClassNodeHierarchy.getAllClassesInHierarchy()) {
            for (MethodNode methodNode : classNode.methods) {
                //replace mixin outer class instance field with the target outer class instance field
                if (mixinClassNodeHierarchy.parent() != null && (methodNode.access & ACC_STATIC) == 0) {
                    for (AbstractInsnNode node : TransformerHelper.getInsnNodes(methodNode.instructions, AbstractInsnNode.FIELD_INSN, GETFIELD, mixinClassNode.name, getOuterClassInstanceFieldName(mixinClassNode, targetClass), "L" + mixinClassNodeHierarchy.parent().classNode().name + ";")) {
                        FieldInsnNode fieldInsnNode = (FieldInsnNode) node;

                        String type = Type.getType(TransformerHelper.getTargetClass(mixinClassNode)).getInternalName();
                        String outerType = Type.getType(TransformerHelper.getTargetClass(mixinClassNodeHierarchy.parent().classNode())).getDescriptor();
                        methodNode.instructions.insertBefore(fieldInsnNode, new FieldInsnNode(GETFIELD, type, fieldInsnNode.name, outerType));
                        methodNode.instructions.remove(fieldInsnNode);
                    }
                }

                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if(insnNode instanceof TypeInsnNode typeInsnNode && typeInsnNode.getOpcode() == CHECKCAST) {
                        if(typeInsnNode.desc.equals(mixinClassNode.name) || typeInsnNode.desc.equals(targetClassNode.name)) {
                            methodNode.instructions.remove(typeInsnNode);
                        }
                    }
                    if(insnNode instanceof MethodInsnNode methodInsnNode && methodInsnNode.owner.equals(mixinClassNode.name)) {
                        MethodNode nodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(methodInsnNode.name + methodInsnNode.desc)).findAny().orElse(null);
                        if(nodeMethodNode != null) {
                            MethodInsnNode invoke = editors.mixinMethodEditors().get(nodeMethodNode).invoke;
                            if(invoke == null) invoke = new MethodInsnNode(methodInsnNode.getOpcode(), targetClassNode.name, replaceName + methodInsnNode.name, methodInsnNode.desc);

                            methodNode.instructions.insertBefore(insnNode, invoke);
                            methodNode.instructions.remove(methodInsnNode);
                        }
                    }
                    if(insnNode instanceof InvokeDynamicInsnNode invokeDynamicInsnNode) {
                        if(invokeDynamicInsnNode.desc.startsWith("(L" + mixinClassNode.name)) {
                            invokeDynamicInsnNode.desc = "(L" + targetClassNode.name + invokeDynamicInsnNode.desc.substring(2 + mixinClassNode.name.length());
                        }
                        invokeDynamicInsnNode.bsmArgs = Arrays.stream(invokeDynamicInsnNode.bsmArgs).map(arg -> {
                            if(arg instanceof Handle handle && handle.getOwner().equals(mixinClassNode.name)) {
                                MethodNode bsmNodeMethodNode = mixinClassNode.methods.stream().filter(m -> (m.name + m.desc).equals(handle.getName() + handle.getDesc())).findAny().orElse(null);
                                if (bsmNodeMethodNode != null) {
                                    return new Handle(H_INVOKESTATIC, handle.getOwner(), handle.getName(), handle.getTag() == H_INVOKESTATIC ? handle.getDesc() : getNewDesc(handle.getDesc(), targetClassNode.name), handle.isInterface());
                                }
                            }
                            return arg;
                        }).toArray(Object[]::new);
                    }
                    if(insnNode instanceof FieldInsnNode fieldInsnNode) {
                        FieldNode nodeFieldNode = mixinClassNode.fields.stream().filter(m -> m.name.equals(fieldInsnNode.name)).findAny().orElse(null);
                        if(nodeFieldNode != null && (nodeFieldNode.access & ACC_SYNTHETIC) == 0) {
                            FieldInsnNode change;
                            if (fieldInsnNode.getOpcode() == PUTFIELD || fieldInsnNode.getOpcode() == PUTSTATIC) change = editors.mixinFieldEditors().get(nodeFieldNode).set;
                            else change = editors.mixinFieldEditors().get(nodeFieldNode).get;
                            if(change == null) change = new FieldInsnNode(fieldInsnNode.getOpcode(), targetClassNode.name, replaceName + fieldInsnNode.name, fieldInsnNode.desc);
                            methodNode.instructions.insertBefore(insnNode, change);
                            methodNode.instructions.remove(fieldInsnNode);
                        }
                    }
                }
            }
        }

        //move fields to target class
        editors.mixinFieldEditors().forEach((fieldNode, fieldEditor) -> {
            if(fieldNode == null) return;

            if(fieldEditor.copy) {
                targetClassEditor.addField(new FieldNode((fieldNode.access & ~(ACC_PRIVATE | ACC_PROTECTED)) | ACC_PUBLIC, replaceName + fieldNode.name, fieldNode.desc, fieldNode.signature, fieldNode.value));
            }
            mixinClassNode.fields.remove(fieldNode);
        });

        editors.mixinMethodEditors().forEach((methodNode, methodEditor) -> {
            if(methodNode == null || methodNode.name.startsWith("<")) return;

            if(methodEditor.delete) {
                mixinClassNode.methods.remove(methodNode);
                return;
            }

            methodNode.access &= ~ACC_SYNTHETIC;
            String oldDesc = methodNode.desc;
            int oldAccess = methodNode.access;
            if ((methodNode.access & ACC_STATIC) == 0) methodNode.desc = getNewDesc(methodNode.desc, targetClassNode.name);
            methodNode.access |= ACC_STATIC;

            if(methodEditor.copy) {
                if((methodNode.access & ACC_ABSTRACT) != 0) throw new IllegalStateException("cannot copy the abstract method " + mixinClassNode.name + "." + methodNode.name + methodNode.desc);

                //make methods acts like instance methods of target
                methodNode.access &= ~ACC_PRIVATE;
                methodNode.access &= ~ACC_PROTECTED;
                methodNode.access |= ACC_PUBLIC;

                //add invokers in target class
                MethodNode addMethodNode = new MethodNode(oldAccess, replaceName + methodNode.name, oldDesc, methodNode.signature, methodNode.exceptions.toArray(String[]::new));
                targetClassEditor.addMethod(addMethodNode);
                boolean isStatic = (addMethodNode.access & ACC_STATIC) != 0;

                addMethodNode.access &= ~ACC_PRIVATE;
                addMethodNode.access &= ~ACC_PROTECTED;
                addMethodNode.access |= ACC_PUBLIC;

                int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(addMethodNode.desc));

                addLoadOpcodesOfMethod(addMethodNode.instructions, Type.getArgumentTypes(addMethodNode.desc), isStatic);
                addMethodNode.instructions.add(new MethodInsnNode(INVOKESTATIC, mixinClassNode.name, methodNode.name, methodNode.desc));
                addMethodNode.instructions.add(new InsnNode(returnOpcode));
            }
        });

        for (MethodNode methodNode : targetClassNode.methods) {
            for (int index : TransformerHelper.getInsnNodeIndexes(methodNode.instructions, AbstractInsnNode.INVOKE_DYNAMIC_INSN, INVOKEDYNAMIC)) {
                if(!(methodNode.instructions.get(index) instanceof InvokeDynamicInsnNode invokeDynamicInsnNode)) continue;
                MixinClassTargetInsnListEditor insnListEditor = targetClassEditor.getMethodEditor(methodNode.name + methodNode.desc).getInsnListEditor();

                methodNode.instructions.remove(invokeDynamicInsnNode);
                Object[] oldBsmArgs = invokeDynamicInsnNode.bsmArgs;
                invokeDynamicInsnNode.bsmArgs = new Object[oldBsmArgs.length +1];
                System.arraycopy(oldBsmArgs, 0, invokeDynamicInsnNode.bsmArgs, 1, oldBsmArgs.length);
                invokeDynamicInsnNode.bsmArgs[0] = invokeDynamicInsnNode.bsm;

                invokeDynamicInsnNode.bsm = new Handle(H_INVOKESTATIC, Type.getInternalName(LambdaHandler.class), "wrapper", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;", false);
                insnListEditor.insertBefore(index, invokeDynamicInsnNode);
                insnListEditor.remove(index);
            }
        }

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
        assert mixinInit != null;
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
            MixinClassTargetInsnListEditor insnListEditor = methodEditor.getInsnListEditor();
            for (int index : TransformerHelper.getInsnNodeIndexes(methodEditor.getInsnListEditor().getInsnListClone(), AbstractInsnNode.INSN, RETURN)) {
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
