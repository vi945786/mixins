package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.annotations.methods.Setter;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;

public class SetterTransformer implements MethodTransformer<Setter> {

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Setter mixinAnnotation, ClassEditor targetClassEditor) {
        FieldEditor targetFieldEditor = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        targetFieldEditor.makeNonFinal();

        MethodNode instanceMethod = new MethodNode(mixinMethodEditor.getAccess() & ~ACC_ABSTRACT, mixinMethodEditor.getName(), mixinMethodEditor.getDesc(), mixinMethodEditor.getSignature(), mixinMethodEditor.getExceptions().toArray(String[]::new));
        targetClassEditor.addMethod(new MethodEditor(instanceMethod));

        Type agrumentType = Type.getArgumentTypes(instanceMethod.desc)[0];
        int staticOffset = (targetFieldEditor.getAccess() & ACC_STATIC) == 0 ? 1 : 0;
        int argumentOpcode = switch (agrumentType.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> ILOAD;
            case Type.FLOAT -> FLOAD;
            case Type.LONG -> LLOAD;
            case Type.DOUBLE -> DLOAD;
            default -> ALOAD;
        };
        instanceMethod.instructions.clear();
        if (staticOffset == 1) instanceMethod.instructions.add(new VarInsnNode(ALOAD, 0));
        instanceMethod.instructions.add(new VarInsnNode(argumentOpcode, staticOffset));
        instanceMethod.instructions.add(new FieldInsnNode(staticOffset == 1 ? PUTFIELD : PUTSTATIC, targetClassEditor.getName(), targetFieldEditor.getName(), targetFieldEditor.getDesc()));
        instanceMethod.instructions.add(new InsnNode(RETURN));
    }
}
