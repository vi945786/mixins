package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.editors.*;
import vi.mixin.api.transformers.ClassTransformer;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.bytecode.Mixiner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static vi.mixin.api.transformers.TransformerHelper.addLoadOpcodesOfMethod;
import static vi.mixin.api.transformers.TransformerHelper.getOuterClassInstanceFieldName;

public class MixinTransformer implements ClassTransformer<Mixin> {

    private ClassEditor mixinClassEditor;
    private Mixin mixinAnnotation;
    private ClassEditor targetClassEditor;
    private String replaceName;


    private void validate() {
        boolean isExtend = mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) != null;
        boolean isInterface = (mixinClassEditor.getAccess() & Opcodes.ACC_INTERFACE) != 0;

        for (MethodEditor methodEditor : mixinClassEditor.getMethodEditors()) {
            if (!isInterface && !isExtend && (methodEditor.getAccess() & Opcodes.ACC_PRIVATE) == 0 && !methodEditor.getName().startsWith("<")) throw new MixinFormatException("method " + mixinClassEditor.getName() + "." + methodEditor.getName(), "must be private");
        }

        for (FieldEditor fieldEditor : mixinClassEditor.getFieldEditors()) {
            if (!isInterface && !isExtend && (fieldEditor.getAccess() & Opcodes.ACC_PRIVATE) == 0 && (fieldEditor.getAccess() & Opcodes.ACC_SYNTHETIC) == 0) throw new MixinFormatException("field " + mixinClassEditor.getName() + "." + fieldEditor.getName(), "must be private");
        }

        if (!isInterface && !isExtend && mixinClassEditor.getMethodEditors("<init>").size() == 2) throw new MixinFormatException(mixinClassEditor.getName(), "has 2 constructors");
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, Mixin mixinAnnotation, ClassEditor targetClassEditor) {
        this.mixinClassEditor = mixinClassEditor;
        this.mixinAnnotation = mixinAnnotation;
        this.targetClassEditor = targetClassEditor;
        validate();
        if ((mixinClassEditor.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
            targetClassEditor.addInterface(mixinClassEditor.getName());
        }

        String targetOuterInstanceFieldName = getOuterClassInstanceFieldName(targetClassEditor);
        if(mixinClassEditor.getOuterClass() != null) addOuterClassInstanceFieldToTarget(targetOuterInstanceFieldName);

        addExceptionToConstructor();

        replaceName = mixinClassEditor.getName().replace("/", "$$") + "$$";
        //move fields to target class
        mixinClassEditor.getFieldEditors().forEach(fieldEditor -> {
            if(fieldEditor.getAnnotationEditors().stream().map(AnnotationEditor::getDesc).anyMatch(Mixiner::hasTransformer) || (fieldEditor.getAccess() & ACC_SYNTHETIC) != 0) return;

            if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) == null && (mixinClassEditor.getAccess() & ACC_INTERFACE) == 0) {
                FieldEditor addFieldEditor = new FieldEditor(new FieldNode(fieldEditor.getAccess(), replaceName + fieldEditor.getName(), fieldEditor.getDesc(), fieldEditor.getSignature(), fieldEditor.getDefaultValue()));
                targetClassEditor.addField(addFieldEditor);
                addFieldEditor.makePublic();
                mixinClassEditor.removeField(fieldEditor);
            }
        });

        //add init and static init to target class for default field values
        if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) == null && (mixinClassEditor.getAccess() & ACC_INTERFACE) == 0) {
            addStaticInit();
            addInit();
        }

        mixinClassEditor.getMethodEditors().forEach(methodEditor -> {
            if(methodEditor.getAnnotationEditors().stream().map(AnnotationEditor::getDesc).anyMatch(Mixiner::hasTransformer) || methodEditor.getName().startsWith("<")) return;

            //make methods acts like instance methods of target
            String desc = methodEditor.getDesc();
            if ((methodEditor.getAccess() & ACC_STATIC) == 0) desc = "(L" + targetClassEditor.getName() + ";" + methodEditor.getDesc().substring(1);
            methodEditor.setDesc(desc);
            methodEditor.makeStatic().makePublic();

            //add invokers in target class
            if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) == null) {
                MethodEditor addMethodEditor = new MethodEditor(new MethodNode(methodEditor.getAccess() & ~ACC_ABSTRACT, replaceName + methodEditor.getName(), methodEditor.getDesc(), methodEditor.getSignature(), methodEditor.getExceptions().toArray(String[]::new)));
                targetClassEditor.addMethod(addMethodEditor);
                addMethodEditor.makePublic();
                boolean isStatic = (addMethodEditor.getAccess() & ACC_STATIC) != 0;

                int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(addMethodEditor.getDesc()));

                List<AbstractInsnNode> insnNodes = new ArrayList<>();
                addLoadOpcodesOfMethod(insnNodes, Type.getArgumentTypes(addMethodEditor.getDesc()), isStatic);
                insnNodes.add(new MethodInsnNode(INVOKESTATIC, mixinClassEditor.getName(), methodEditor.getName(), desc));
                insnNodes.add(new InsnNode(returnOpcode));
                addMethodEditor.getBytecodeEditor().add(0, insnNodes);
            }

            if (mixinClassEditor.getOuterClass() == null || (methodEditor.getAccess() & ACC_STATIC) != 0) return;
            //replace mixin outer class instance field with the target outer class instance field
            for (int index : methodEditor.getBytecodeEditor().getInsnNodesIndexes(AbstractInsnNode.FIELD_INSN, GETFIELD, mixinClassEditor.getName(), TransformerHelper.getOuterClassInstanceFieldName(mixinClassEditor), "L" + mixinClassEditor.getOuterClass().getName() + ";")) {
                FieldInsnNode fieldInsnNode = (FieldInsnNode) methodEditor.getBytecodeEditor().get(index);
                if (!fieldInsnNode.name.equals(targetOuterInstanceFieldName)) return;

                String type = Type.getType(mixinClassEditor.getTargetClass()).getInternalName();
                String outerType = Type.getType(mixinClassEditor.getOuterClass().getTargetClass()).getDescriptor();
                methodEditor.getBytecodeEditor().add(index, new FieldInsnNode(GETFIELD, type, fieldInsnNode.name, outerType));
                methodEditor.getBytecodeEditor().remove(index);
            }
        });

        for (ClassEditor classEditor : mixinClassEditor.getAllClassesInHierarchy()) {
            for (MethodEditor methodEditor : classEditor.getMethodEditors()) {
                if(methodEditor.getName().startsWith("<")) continue;
                BytecodeEditor bytecodeEditor = methodEditor.getBytecodeEditor();
                replaceBytecode(bytecodeEditor);
            }
        }
    }

    private void addOuterClassInstanceFieldToTarget(String targetOuterFieldName) {
        Type outerType = Type.getType(targetClassEditor.getRealClass().getDeclaringClass());

        FieldEditor targetOuterFieldEditor = targetClassEditor.getFieldEditor(targetOuterFieldName);
        if(targetOuterFieldEditor == null) {
            targetOuterFieldEditor = new FieldEditor(new FieldNode(Opcodes.ACC_PUBLIC, targetOuterFieldName, outerType.getDescriptor(), null, null));
            targetClassEditor.addField(targetOuterFieldEditor);

            targetClassEditor.getMethodEditors().stream().filter(methodEditor -> methodEditor.getName().equals("<init>")).forEach(methodEditor -> {
                methodEditor.getBytecodeEditor().add(0, new VarInsnNode(ALOAD, 0));
                methodEditor.getBytecodeEditor().add(0, new VarInsnNode(ALOAD, 1));
                methodEditor.getBytecodeEditor().add(0, new FieldInsnNode(PUTFIELD, targetClassEditor.getName(), targetOuterFieldName, outerType.getDescriptor()));
            });
        } else {
            targetOuterFieldEditor.setAccess(targetOuterFieldEditor.getAccess() & ~Opcodes.ACC_SYNTHETIC & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC);
        }
    }

    private void addStaticInit() {
        MethodEditor mixinClinit = mixinClassEditor.getMethodEditor("<clinit>()V");
        if (mixinClinit != null) {
            MethodEditor targetClinit = targetClassEditor.getMethodEditor("<clinit>()V");
            if (targetClinit == null) {
                targetClassEditor.addMethod(mixinClinit);
                replaceBytecode(mixinClinit.getBytecodeEditor());
            } else {
                List<AbstractInsnNode> insnNodes = replaceBytecode(mixinClinit.getBytecodeEditor().getBytecode());
                for (int i = 0; i < targetClinit.getBytecodeEditor().getBytecode().size(); i++) {
                    if (targetClinit.getBytecodeEditor().get(i).getOpcode() == RETURN)
                        targetClinit.getBytecodeEditor().add(i, insnNodes);
                }
            }

        }
    }

    private void addInit() {
        MethodEditor mixinInit = mixinClassEditor.getMethodEditors("<init>").getFirst();
        if (mixinInit != null) {
            List<AbstractInsnNode> insnNodes = new ArrayList<>();

            boolean sawNew = false;
            boolean afterSuper = false;
            for (AbstractInsnNode node : mixinInit.getBytecodeEditor().getBytecode()) {
                if (afterSuper) {
                    insnNodes.add(node);
                    continue;
                }
                if (node.getOpcode() == NEW) sawNew = true;
                if (node.getOpcode() == INVOKESPECIAL && node instanceof MethodInsnNode methodInsnNode && methodInsnNode.name.equals("<init>")) {
                    if (sawNew) sawNew = false;
                    else afterSuper = true;
                }
            }
            insnNodes = replaceBytecode(insnNodes);

            for (MethodEditor targetInit : targetClassEditor.getMethodEditors("<init>")) {
                for (int i : targetInit.getBytecodeEditor().getInsnNodesIndexes(AbstractInsnNode.INSN, RETURN)) {
                    targetInit.getBytecodeEditor().add(i, insnNodes);
                }
            }
        }
    }

    private void replaceBytecode(BytecodeEditor bytecodeEditor) {
        for (int i = 0; i < bytecodeEditor.getBytecode().size(); i++) {
            AbstractInsnNode insnNode = bytecodeEditor.getBytecode().get(i);
            if(insnNode instanceof TypeInsnNode typeInsnNode && typeInsnNode.getOpcode() == CHECKCAST) {
                if(typeInsnNode.desc.equals(mixinClassEditor.getName()) || typeInsnNode.desc.equals(targetClassEditor.getName())) {
                    bytecodeEditor.remove(i);
                }
            }
            if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) != null || (mixinClassEditor.getAccess() & ACC_INTERFACE) != 0) continue;
            if(insnNode instanceof MethodInsnNode methodInsnNode && methodInsnNode.owner.equals(mixinClassEditor.getName())) {
                MethodEditor nodeMethodEditor = mixinClassEditor.getMethodEditor(methodInsnNode.name + methodInsnNode.desc);
                if(nodeMethodEditor != null && nodeMethodEditor.getAnnotationEditors().stream().map(AnnotationEditor::getDesc).noneMatch(Mixiner::hasTransformer)) {
                    bytecodeEditor.add(i, new MethodInsnNode(methodInsnNode.getOpcode(), targetClassEditor.getName(), replaceName + methodInsnNode.name, methodInsnNode.desc));
                    bytecodeEditor.remove(i);
                }
            }
            if(insnNode instanceof FieldInsnNode fieldInsnNode && fieldInsnNode.owner.equals(mixinClassEditor.getName())) {
                FieldEditor nodeFieldEditor = mixinClassEditor.getFieldEditor(fieldInsnNode.name);
                if(nodeFieldEditor != null && (nodeFieldEditor.getAccess() & ACC_SYNTHETIC) == 0 && nodeFieldEditor.getAnnotationEditors().stream().map(AnnotationEditor::getDesc).noneMatch(Mixiner::hasTransformer)) {
                    bytecodeEditor.add(i, new FieldInsnNode(fieldInsnNode.getOpcode(), targetClassEditor.getName(), replaceName + fieldInsnNode.name, fieldInsnNode.desc));
                    bytecodeEditor.remove(i);
                }
            }
        }
    }

    private List<AbstractInsnNode> replaceBytecode(List<AbstractInsnNode> insnNodes) {
        InsnList insnList = new InsnList();
        for (AbstractInsnNode node : insnNodes) {
            insnList.add(node);
        }

        BytecodeEditor editor = new BytecodeEditor(insnList);
        replaceBytecode(editor);

        return Arrays.stream(insnList.toArray()).toList();
    }

    private void addExceptionToConstructor() {
        if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) == null) {
            mixinClassEditor.getMethodEditors().stream().filter(methodEditor -> methodEditor.getName().equals("<init>")).forEach(methodEditor -> {
                List<AbstractInsnNode> bytecode = methodEditor.getBytecodeEditor().getBytecode();
                for (int i = 0; i < bytecode.size(); i++) {
                    AbstractInsnNode insnNode = bytecode.get(i);
                    if (insnNode.getOpcode() != RETURN) continue;

                    methodEditor.getBytecodeEditor().add(i, new TypeInsnNode(NEW, Type.getType(UnsupportedOperationException.class).getInternalName()));
                    methodEditor.getBytecodeEditor().add(i, new InsnNode(DUP));
                    methodEditor.getBytecodeEditor().add(i, new LdcInsnNode("attempted to invoke " + mixinClassEditor.getName() + "." + methodEditor.getName() + methodEditor.getDesc()));
                    methodEditor.getBytecodeEditor().add(i, new MethodInsnNode(INVOKESPECIAL, Type.getType(UnsupportedOperationException.class).getInternalName(), "<init>", "(Ljava/lang/String;)V"));
                    methodEditor.getBytecodeEditor().add(i, new InsnNode(ATHROW));
                    methodEditor.getBytecodeEditor().add(i, new InsnNode(ATHROW));
                    break;
                }
            });
        }
    }
}
