package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.annotations.methods.Inject;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;

import java.util.ArrayList;
import java.util.List;

public class InjectTransformer implements MethodTransformer<Inject> {

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Inject mixinAnnotation, ClassEditor targetClassEditor) {
        MethodEditor targetMethodEditor = targetClassEditor.getMethodEditor(mixinAnnotation.method());
        List<Integer> indexes = targetMethodEditor.getBytecodeEditor().getAtTargetIndexes(mixinAnnotation.at());

        Type returnType = Type.getReturnType(targetMethodEditor.getDesc());
        int staticOffset = (targetMethodEditor.getAccess() & ACC_STATIC) == 0 ? 1 : 0;
        String returner = returnType.getSort() == 0 ? "vi/mixin/api/injection/Returner" : "vi/mixin/api/injection/ValueReturner";
        mixinMethodEditor.makeStatic().makePublic();
        String desc = mixinMethodEditor.getDesc();
        if(staticOffset == 1) desc = "(L" + targetClassEditor.getName() + ";" + mixinMethodEditor.getDesc().substring(1);
        mixinMethodEditor.setDesc(desc);


        for(int index : indexes) {
            List<AbstractInsnNode> insnNodes = new ArrayList<>();

            insnNodes.add(new TypeInsnNode(NEW, returner));
            insnNodes.add(new InsnNode(DUP));
            insnNodes.add(new MethodInsnNode(INVOKESPECIAL, returner, "<init>", "()V"));
            insnNodes.add(new InsnNode(DUP));

            if(staticOffset == 1) {
                insnNodes.add(new VarInsnNode(ALOAD, 0));
                insnNodes.add(new InsnNode(SWAP));
            }
            Type[] arguments = Type.getArgumentTypes(targetMethodEditor.getDesc());

            for (int i = 0; i < arguments.length; i++) {
                Type argument = arguments[i];
                int argumentOpcode = switch (argument.getSort()) {
                    case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> ILOAD;
                    case Type.FLOAT -> FLOAD;
                    case Type.LONG -> LLOAD;
                    case Type.DOUBLE -> DLOAD;
                    default -> ALOAD;
                };
                insnNodes.add(new VarInsnNode(argumentOpcode, i+staticOffset));
                insnNodes.add(new InsnNode(SWAP));
            }

            insnNodes.add(new MethodInsnNode(INVOKESTATIC, mixinClassEditor.getName(), mixinMethodEditor.getName(), desc));

            LabelNode skipReturn = new LabelNode();
            if(returnType.getSort() != 0) insnNodes.add(new InsnNode(DUP));
            insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL, returner, "isReturned", "()Z"));
            insnNodes.add(new JumpInsnNode(IFEQ, skipReturn));
            if(returnType.getSort() != 0) {

                String methodName = "getReturnValue";
                String methodDesc = "()java/lang/Object";
                if (returnType.getSort() <= 8) {
                    methodName += returnType.getInternalName();
                    methodDesc = "()" + returnType.getInternalName();
                }
                insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL, returner, methodName, methodDesc));
            }

            int returnOpcode = switch (returnType.getSort()) {
                case Type.VOID -> RETURN;
                case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> IRETURN;
                case Type.FLOAT -> FRETURN;
                case Type.LONG -> LRETURN;
                case Type.DOUBLE -> DRETURN;
                default -> ARETURN;
            };
            insnNodes.add(new InsnNode(returnOpcode));
            insnNodes.add(skipReturn);
            if(returnType.getSort() != 0) insnNodes.add(new InsnNode(POP));

            targetMethodEditor.getBytecodeEditor().add(index, insnNodes);
        }
    }
}
