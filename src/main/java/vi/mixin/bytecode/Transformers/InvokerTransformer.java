package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.annotations.methods.Invoker;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;

import java.util.ArrayList;
import java.util.List;

public class InvokerTransformer implements MethodTransformer<Invoker> {

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Invoker mixinAnnotation, ClassEditor targetClassEditor) {
        MethodEditor targetMethodEditor = targetClassEditor.getMethodEditor(mixinAnnotation.value());

        MethodEditor instanceMethod;
        if(!targetMethodEditor.getName().equals(mixinMethodEditor.getName())) {
            instanceMethod = new MethodEditor(new MethodNode(mixinMethodEditor.getAccess() & ~ACC_ABSTRACT, mixinMethodEditor.getName(), mixinMethodEditor.getDesc(), mixinMethodEditor.getSignature(), mixinMethodEditor.getExceptions().toArray(String[]::new)));
            targetClassEditor.addMethod(instanceMethod);

            Type returnType = Type.getReturnType(instanceMethod.getDesc());
            int staticOffset = (targetMethodEditor.getAccess() & ACC_STATIC) == 0 ? 1 : 0;
            int invokeOpcode = INVOKEVIRTUAL;
            if(staticOffset == 0) invokeOpcode = INVOKESTATIC;
            else if(instanceMethod.getName().equals("<init>")) invokeOpcode = INVOKESPECIAL;
            else if((targetClassEditor.getAccess() & ACC_INTERFACE) != 0) invokeOpcode = INVOKEINTERFACE;

            int returnOpcode = switch (returnType.getSort()) {
                case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> IRETURN;
                case Type.FLOAT -> FRETURN;
                case Type.LONG -> LRETURN;
                case Type.DOUBLE -> DRETURN;
                default -> ARETURN;
            };

            List<AbstractInsnNode> insnNodes = new ArrayList<>();

            if (staticOffset == 1) insnNodes.add(new VarInsnNode(ALOAD, 0));

            Type[] arguments = Type.getArgumentTypes(instanceMethod.getDesc());
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
            }

            insnNodes.add(new MethodInsnNode(invokeOpcode, targetClassEditor.getName(), targetMethodEditor.getName(), targetMethodEditor.getDesc()));
            insnNodes.add(new InsnNode(returnOpcode));
            instanceMethod.getBytecodeEditor().add(0, insnNodes);
        } else {
            instanceMethod = targetMethodEditor;
            instanceMethod.makePublic();
        }
    }
}
