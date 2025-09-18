package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Getter;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.accessortype.AccessorFieldEditor;
import vi.mixin.api.transformers.accessortype.AccessorMethodTransformer;

public class GetterTransformer implements AccessorMethodTransformer<Getter> {

    public boolean isFieldTarget(MethodNode mixinMethodNodeClone, FieldNode targetFieldNodeClone, Getter annotation) {
        if(annotation.value().isEmpty()) {
            if(!mixinMethodNodeClone.name.startsWith("get")) return false;
            return targetFieldNodeClone.name.equals(mixinMethodNodeClone.name.substring(3, 4).toLowerCase() + mixinMethodNodeClone.name.substring(4));
        }
        return targetFieldNodeClone.name.equals(annotation.value());
    }

    public TargetType getTargetMethodType() {
        return TargetType.FIELD;
    }

    private static void validate(AccessorFieldEditor fieldEditor, Getter annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = fieldEditor.getMixinMethodNodeClone();

        String name = "@Getter " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;

        if(fieldEditor.getNumberOfTargets() != 1) throw new MixinFormatException(name, "illegal number of targets, should be 1");
        FieldNode targetFieldNode = fieldEditor.getTargetFieldNodeClone(0);

        if((targetFieldNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((targetFieldNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.equals(Type.getType(targetFieldNode.desc)) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + targetFieldNode.desc + ", " + Type.getType(Object.class));
        if(Type.getArgumentTypes(mixinMethodNode.desc).length != 0) throw new MixinFormatException(name, "takes arguments");
    }

    @Override
    public void transform(AccessorFieldEditor fieldEditor, Getter annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(fieldEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        MethodNode mixinMethodNode = fieldEditor.getMixinMethodNodeClone();
        FieldNode targetFieldNode = fieldEditor.getTargetFieldNodeClone(0);

        boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;

        int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(mixinMethodNode.desc));

        fieldEditor.makeTargetPublic(0);

        InsnList insnList = new InsnList();
        if (!isStatic) insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
        insnList.add(new InsnNode(returnOpcode));

        fieldEditor.setMixinBytecode(insnList);
    }
}
