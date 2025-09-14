package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Getter;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;
import vi.mixin.api.transformers.TransformerHelper;

public class GetterTransformer implements MethodTransformer<Getter> {

    private static void validate(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Getter mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Getter " + mixinClassEditor.getName() + "." + mixinMethodEditor.getName() + mixinMethodEditor.getDesc();
        FieldEditor target = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        if(target == null) throw new MixinFormatException(name, "target doesn't exist");
        if((target.getAccess() & ACC_STATIC) != (mixinMethodEditor.getAccess() & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((target.getAccess() & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type returnType = Type.getReturnType(mixinMethodEditor.getDesc());
        if(!returnType.equals(Type.getType(target.getDesc())) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + target.getDesc() + ", " + Type.getType(Object.class));
        if(Type.getArgumentTypes(mixinMethodEditor.getDesc()).length != 0) throw new MixinFormatException(name, "takes arguments");
        if((mixinClassEditor.getAccess() & ACC_INTERFACE) == 0) throw new MixinFormatException(name, "defining class is not an interface");
        if(targetClassEditor.getMethodEditors().stream().anyMatch(method -> method.getName().equals(mixinMethodEditor.getName())
                && method.getDesc().split("\\)")[0].equals(mixinMethodEditor.getDesc().split("\\)")[0]))) throw new MixinFormatException(name, "method with this name and desc already exists in the target class.");
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Getter mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinMethodEditor, mixinAnnotation, targetClassEditor);
        FieldEditor targetFieldEditor = targetClassEditor.getFieldEditor(mixinAnnotation.value());

        MethodNode instanceMethod = new MethodNode(mixinMethodEditor.getAccess() & ~ACC_ABSTRACT, mixinMethodEditor.getName(), mixinMethodEditor.getDesc(), mixinMethodEditor.getSignature(), mixinMethodEditor.getExceptions().toArray(String[]::new));
        targetClassEditor.addMethod(new MethodEditor(instanceMethod));

        boolean isStatic = (targetFieldEditor.getAccess() & ACC_STATIC) != 0;
        int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(instanceMethod.desc));

        instanceMethod.instructions.clear();
        if (!isStatic) instanceMethod.instructions.add(new VarInsnNode(ALOAD, 0));
        instanceMethod.instructions.add(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, targetClassEditor.getName(), targetFieldEditor.getName(), targetFieldEditor.getDesc()));
        instanceMethod.instructions.add(new InsnNode(returnOpcode));
    }
}
