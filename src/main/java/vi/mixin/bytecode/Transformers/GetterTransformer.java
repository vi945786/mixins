package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.annotations.methods.Getter;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;

import static org.objectweb.asm.Type.*;

public class GetterTransformer implements MethodTransformer<Getter> {

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Getter mixinAnnotation, ClassEditor targetClassEditor) {
        FieldEditor targetFieldEditor = targetClassEditor.getFieldEditor(mixinAnnotation.value());

        MethodNode instanceMethod = new MethodNode(mixinMethodEditor.getAccess() & ~ACC_ABSTRACT, mixinMethodEditor.getName(), mixinMethodEditor.getDesc(), mixinMethodEditor.getSignature(), mixinMethodEditor.getExceptions().toArray(String[]::new));
        targetClassEditor.addMethod(new MethodEditor(instanceMethod));

        boolean isStatic = (targetFieldEditor.getAccess() & ACC_STATIC) != 0;
        Type returnType = Type.getReturnType(instanceMethod.desc);
        int returnOpcode = switch (returnType.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> IRETURN;
            case Type.FLOAT -> FRETURN;
            case Type.LONG -> LRETURN;
            case Type.DOUBLE -> DRETURN;
            default -> ARETURN;
        };
        instanceMethod.instructions.clear();
        if (!isStatic) instanceMethod.instructions.add(new VarInsnNode(ALOAD, 0));
        instanceMethod.instructions.add(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, targetClassEditor.getName(), targetFieldEditor.getName(), targetFieldEditor.getDesc()));
        instanceMethod.instructions.add(new InsnNode(returnOpcode));
    }
}
