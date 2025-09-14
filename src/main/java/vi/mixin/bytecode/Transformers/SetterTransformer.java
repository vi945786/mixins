package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Setter;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;
import vi.mixin.api.transformers.TransformerHelper;

import java.util.ArrayList;
import java.util.List;

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

        boolean isStatic = (targetFieldEditor.getAccess() & ACC_STATIC) != 0;

        instanceMethod.instructions.clear();
        if (isStatic) instanceMethod.instructions.add(new VarInsnNode(ALOAD, 0));

        List<AbstractInsnNode> insnNodes = new ArrayList<>();
        TransformerHelper.addLoadOpcodesOfMethod(insnNodes, Type.getArgumentTypes(instanceMethod.desc), isStatic);
        for(AbstractInsnNode abstractInsnNode : insnNodes) instanceMethod.instructions.add(abstractInsnNode);

        instanceMethod.instructions.add(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, targetClassEditor.getName(), targetFieldEditor.getName(), targetFieldEditor.getDesc()));
        instanceMethod.instructions.add(new InsnNode(RETURN));
    }
}
