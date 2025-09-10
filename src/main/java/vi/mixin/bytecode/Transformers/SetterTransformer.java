package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Setter;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;

public class SetterTransformer implements MethodTransformer<Setter> {

    private static void validate(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Setter mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Setter " + mixinClassEditor.getName() + "." + mixinMethodEditor.getName() + mixinMethodEditor.getDesc();
        FieldEditor target = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        if(target == null) throw new MixinFormatException(name, "target doesn't exist");
        if((target.getAccess() & ACC_STATIC) != (mixinMethodEditor.getAccess() & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((target.getAccess() & ACC_STATIC) != 0 ? "" : "not") + " static");
        if(!Type.getReturnType(mixinMethodEditor.getDesc()).equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "should return void");
        Type[] argumentTypes = Type.getArgumentTypes(mixinMethodEditor.getDesc());
        if(argumentTypes.length != 1 && !argumentTypes[0].equals(Type.getType(target.getDesc())) && !argumentTypes[0].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid arguments are: " + target.getDesc() + ", " + Type.getType(Object.class));
        if((mixinClassEditor.getAccess() & ACC_INTERFACE) == 0) throw new MixinFormatException(name, "defining class is not an interface");
        if(targetClassEditor.getMethodEditors().stream().anyMatch(method -> method.getName().equals(mixinMethodEditor.getName())
                && method.getDesc().split("\\)")[0].equals(mixinMethodEditor.getDesc().split("\\)")[0]))) throw new MixinFormatException(name, "method with this name and desc already exists in the target class.");
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Setter mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinMethodEditor, mixinAnnotation, targetClassEditor);
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
