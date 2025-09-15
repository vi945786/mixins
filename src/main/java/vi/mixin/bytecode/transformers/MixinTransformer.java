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
import java.util.List;

import static vi.mixin.api.transformers.TransformerHelper.addLoadOpcodesOfMethod;
import static vi.mixin.api.transformers.TransformerHelper.getOuterClassInstanceFieldName;

public class MixinTransformer implements ClassTransformer<Mixin> {

    private static void validate(ClassEditor mixinClassEditor, Mixin mixinAnnotation, ClassEditor targetClassEditor) {
        boolean isExtend = mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) != null;
        boolean isInterface = (mixinClassEditor.getAccess() & Opcodes.ACC_INTERFACE) != 0;

        for (MethodEditor methodEditor : mixinClassEditor.getMethodEditors()) {
            if (!isInterface && !isExtend && (methodEditor.getAccess() & Opcodes.ACC_PRIVATE) == 0 && !methodEditor.getName().startsWith("<")) throw new MixinFormatException("method " + mixinClassEditor.getName() + "." + methodEditor.getName(), "must be private");
        }

        for (FieldEditor fieldEditor : mixinClassEditor.getFieldEditors()) {
            if (!isInterface && !isExtend && (fieldEditor.getAccess() & Opcodes.ACC_PRIVATE) == 0 && (fieldEditor.getAccess() & Opcodes.ACC_SYNTHETIC) == 0) throw new MixinFormatException("field " + mixinClassEditor.getFieldEditors() + "." + fieldEditor.getName(), "must be private");
        }
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, Mixin mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinAnnotation, targetClassEditor);
        if ((mixinClassEditor.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
            targetClassEditor.addInterface(mixinClassEditor.getName());
        }

        String targetOuterFieldName = getOuterClassInstanceFieldName(targetClassEditor);
        if(mixinClassEditor.getOuterClass() != null) {
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

        String replaceName = mixinClassEditor.getName().replace("/", "$$") + "$$";
        mixinClassEditor.getFieldEditors().forEach(fieldEditor -> {
            if(fieldEditor.getAnnotationEditors().stream().map(AnnotationEditor::getDesc).anyMatch(Mixiner::hasTransformer) || (fieldEditor.getAccess() & ACC_SYNTHETIC) != 0 || (fieldEditor.getAccess() & ACC_STATIC) != 0) return;

            if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) == null && (mixinClassEditor.getAccess() & ACC_INTERFACE) == 0) {
                FieldEditor addFieldEditor = new FieldEditor(new FieldNode(fieldEditor.getAccess(), replaceName + fieldEditor.getName(), fieldEditor.getDesc(), fieldEditor.getSignature(), fieldEditor.getDefaultValue()));
                targetClassEditor.addField(addFieldEditor);
                addFieldEditor.makePublic();
                mixinClassEditor.removeField(fieldEditor);
            }
        });

        MethodEditor mixinClinit = mixinClassEditor.getMethodEditor("<clinit>()V");
        if (mixinClinit != null) {
            MethodEditor targetClinit = targetClassEditor.getMethodEditor("<clinit>()V");
            if(targetClinit == null) targetClassEditor.addMethod(mixinClinit);
            else {
                for (int i = 0; i < targetClinit.getBytecodeEditor().getBytecode().size(); i++) {
                    if(targetClinit.getBytecodeEditor().get(i).getOpcode() == RETURN)
                        targetClinit.getBytecodeEditor().add(i, mixinClinit.getBytecodeEditor().getBytecode());
                }
            }
        }

        if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) == null && (mixinClassEditor.getAccess() & ACC_INTERFACE) == 0) {
            MethodEditor mixinInit = mixinClassEditor.getMethodEditors("<init>").getFirst();
            if (mixinInit != null) {
                List<AbstractInsnNode> insnNodes = new ArrayList<>();

                boolean sawNew = false;
                boolean afterSuper = false;
                for (int i = 0; i < mixinInit.getBytecodeEditor().getBytecode().size(); i++) {
                    AbstractInsnNode node = mixinInit.getBytecodeEditor().getBytecode().get(i);
                    if (afterSuper) {
                        if(node.getOpcode() == RETURN) continue;

                        if(node instanceof FieldInsnNode fieldInsnNode && fieldInsnNode.owner.equals(mixinClassEditor.getName())) {
                            FieldEditor nodeFieldEditor = mixinClassEditor.getFieldEditor(fieldInsnNode.name);
                            if(nodeFieldEditor != null && (nodeFieldEditor.getAccess() & ACC_SYNTHETIC) == 0 && (nodeFieldEditor.getAccess() & ACC_STATIC) == 0 && nodeFieldEditor.getAnnotationEditors().stream().map(AnnotationEditor::getDesc).noneMatch(Mixiner::hasTransformer)) {
                                insnNodes.add(new FieldInsnNode(fieldInsnNode.getOpcode(), targetClassEditor.getName(), replaceName + fieldInsnNode.name, fieldInsnNode.desc));
                            }
                        } else {
                            insnNodes.add(node);
                        }
                        continue;
                    }
                    if (node.getOpcode() == NEW) sawNew = true;
                    if (node.getOpcode() == INVOKESPECIAL && node instanceof MethodInsnNode methodInsnNode && methodInsnNode.name.equals("<init>")) {
                        if (sawNew) sawNew = false;
                        else afterSuper = true;
                    }
                }

                targetClassEditor.getMethodEditors("<init>").forEach(targetInit -> {
                    targetInit.getBytecodeEditor().add(mixinInit.getBytecodeEditor().getBytecode());

                    boolean targetSawNew = false;
                    boolean targetAfterSuper = false;
                    for (int i = 0; i < targetInit.getBytecodeEditor().getBytecode().size(); i++) {
                        AbstractInsnNode node = targetInit.getBytecodeEditor().getBytecode().get(i);
                        if (targetAfterSuper) {
                            targetInit.getBytecodeEditor().add(i, insnNodes);
                            break;
                        }
                        if (node.getOpcode() == NEW) targetSawNew = true;
                        if (node.getOpcode() == INVOKESPECIAL && node instanceof MethodInsnNode methodInsnNode && methodInsnNode.name.equals("<init>")) {
                            if (targetSawNew) targetSawNew = false;
                            else targetAfterSuper = true;
                        }
                    }
                });
            }
        }

        mixinClassEditor.getMethodEditors().forEach(methodEditor -> {
            if(methodEditor.getAnnotationEditors().stream().map(AnnotationEditor::getDesc).anyMatch(Mixiner::hasTransformer) || methodEditor.getName().startsWith("<") || (methodEditor.getAccess() & ACC_STATIC) != 0) return;

            if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) == null && (mixinClassEditor.getAccess() & ACC_INTERFACE) == 0) {
                MethodEditor addMethodEditor = new MethodEditor(new MethodNode(methodEditor.getAccess() & ~ACC_ABSTRACT, replaceName + methodEditor.getName(), methodEditor.getDesc(), methodEditor.getSignature(), methodEditor.getExceptions().toArray(String[]::new)));
                targetClassEditor.addMethod(addMethodEditor);
                addMethodEditor.makePublic();
                boolean isStatic = (addMethodEditor.getAccess() & ACC_STATIC) != 0;

                int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(addMethodEditor.getDesc()));

                List<AbstractInsnNode> insnNodes = new ArrayList<>();
                addLoadOpcodesOfMethod(insnNodes, Type.getArgumentTypes(addMethodEditor.getDesc()), isStatic);
                insnNodes.add(new MethodInsnNode(INVOKESTATIC, mixinClassEditor.getName(), methodEditor.getName(), "(L" + targetClassEditor.getName() + ";" + methodEditor.getDesc().substring(1)));
                insnNodes.add(new InsnNode(returnOpcode));
                addMethodEditor.getBytecodeEditor().add(0, insnNodes);
            }

            methodEditor.makeStatic().makePublic();
            String desc = methodEditor.getDesc();
            if ((methodEditor.getAccess() & ACC_STATIC) == 0) desc = "(L" + targetClassEditor.getName() + ";" + methodEditor.getDesc().substring(1);
            methodEditor.setDesc(desc);

            if (mixinClassEditor.getOuterClass() == null) return;
            for (int index : methodEditor.getBytecodeEditor().getInsnNodesIndexes(AbstractInsnNode.FIELD_INSN, GETFIELD, mixinClassEditor.getName(), null, "L" + mixinClassEditor.getOuterClass().getName() + ";")) {
                FieldInsnNode fieldInsnNode = (FieldInsnNode) methodEditor.getBytecodeEditor().get(index);
                if (!fieldInsnNode.name.equals(targetOuterFieldName)) return;

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
                for (int i = 0; i < bytecodeEditor.getBytecode().size(); i++) {
                    AbstractInsnNode insnNode = bytecodeEditor.getBytecode().get(i);
                    if(insnNode instanceof TypeInsnNode typeInsnNode && typeInsnNode.getOpcode() == CHECKCAST) {
                        if(typeInsnNode.desc.equals(mixinClassEditor.getName()) || typeInsnNode.desc.equals(targetClassEditor.getName())) bytecodeEditor.remove(i);
                    }
                    if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) != null || (mixinClassEditor.getAccess() & ACC_INTERFACE) != 0) continue;
                    if(insnNode instanceof MethodInsnNode methodInsnNode && methodInsnNode.owner.equals(mixinClassEditor.getName())) {
                        MethodEditor nodeMethodEditor = mixinClassEditor.getMethodEditor(methodInsnNode.name + methodInsnNode.desc);
                        if(nodeMethodEditor != null && (nodeMethodEditor.getAccess() & ACC_STATIC) == 0 && nodeMethodEditor.getAnnotationEditors().stream().map(AnnotationEditor::getDesc).noneMatch(Mixiner::hasTransformer)) {
                            bytecodeEditor.add(i, new MethodInsnNode(methodInsnNode.getOpcode(), targetClassEditor.getName(), replaceName + methodInsnNode.name, methodInsnNode.desc));
                            bytecodeEditor.remove(i);
                        }
                    }
                    if(insnNode instanceof FieldInsnNode fieldInsnNode && fieldInsnNode.owner.equals(mixinClassEditor.getName())) {
                        FieldEditor nodeFieldEditor = mixinClassEditor.getFieldEditor(fieldInsnNode.name);
                        if(nodeFieldEditor != null && (nodeFieldEditor.getAccess() & ACC_SYNTHETIC) == 0 && (nodeFieldEditor.getAccess() & ACC_STATIC) == 0 && nodeFieldEditor.getAnnotationEditors().stream().map(AnnotationEditor::getDesc).noneMatch(Mixiner::hasTransformer)) {
                            bytecodeEditor.add(i, new FieldInsnNode(fieldInsnNode.getOpcode(), targetClassEditor.getName(), replaceName + fieldInsnNode.name, fieldInsnNode.desc));
                            bytecodeEditor.remove(i);
                        }
                    }
                }
            }
        }
    }
}
