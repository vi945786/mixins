package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Setter;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.accessortype.AccessorFieldEditor;
import vi.mixin.api.transformers.accessortype.AccessorMethodTransformer;

public class SetterTransformer implements AccessorMethodTransformer<Setter> {

    public boolean isFieldTarget(MethodNode mixinMethodNodeClone, FieldNode targetFieldNodeClone, Setter annotation) {
        if(annotation.value().isEmpty()) {
            if(!mixinMethodNodeClone.name.startsWith("set")) return false;
            return targetFieldNodeClone.name.equals(mixinMethodNodeClone.name.substring(3, 4).toLowerCase() + mixinMethodNodeClone.name.substring(4));
        }
        return targetFieldNodeClone.name.equals(annotation.value());
    }

    public TargetType getTargetMethodType() {
        return TargetType.FIELD;
    }

    private static void validate(AccessorFieldEditor fieldEditor, Setter annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = fieldEditor.getMixinMethodNodeClone();

        String name = "@Setter " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if(fieldEditor.getNumberOfTargets() != 1) throw new MixinFormatException(name, "illegal number of targets, should be 1");
        FieldNode targetFieldNode = fieldEditor.getTargetFieldNodeClone(0);

        if((targetFieldNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((targetFieldNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        if(!Type.getReturnType(mixinMethodNode.desc).equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "should return void");
        Type[] argumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        if(argumentTypes.length != 1 && !argumentTypes[0].equals(Type.getType(targetFieldNode.desc)) && !argumentTypes[0].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid arguments are: " + targetFieldNode.desc + ", " + Type.getType(Object.class));
    }

    @Override
    public void transform(AccessorFieldEditor fieldEditor, Setter annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(fieldEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        MethodNode mixinMethodNode = fieldEditor.getMixinMethodNodeClone();
        FieldNode targetFieldNode = fieldEditor.getTargetFieldNodeClone(0);

        fieldEditor.makeTargetPublic(0);
        fieldEditor.makeTargetNonFinal(0);

        boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;

        InsnList insnList = new InsnList();
        TransformerHelper.addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(mixinMethodNode.desc), isStatic);
        insnList.add(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
        insnList.add(new InsnNode(RETURN));

        fieldEditor.setMixinBytecode(insnList);
    }
}
