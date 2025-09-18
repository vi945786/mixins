package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Overridable;
import vi.mixin.api.transformers.extendertype.ExtenderMethodEditor;
import vi.mixin.api.transformers.extendertype.ExtenderMethodTransformer;

public class OverridableTransformer implements ExtenderMethodTransformer<Overridable> {

    private static void validate(ExtenderMethodEditor methodEditor, Overridable annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = methodEditor.getMixinMethodNodeClone();

        String name = "@Overridable " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if(methodEditor.getNumberOfTargets() != 1) throw new MixinFormatException(name, "illegal number of targets, should be 1");
        MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

        if(targetMethodNode.name.equals("<init>")) throw new MixinFormatException(name, "overriding a constructor is not allowed. use @New");
        if((mixinMethodNode.access & ACC_STATIC) != 0) throw new MixinFormatException(name, "should be not static");
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.equals(Type.getReturnType(targetMethodNode.desc)) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + Type.getReturnType(targetMethodNode.desc) + ", " + Type.getType(Object.class));
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
    }

    @Override
    public void transform(ExtenderMethodEditor methodEditor, Overridable annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(methodEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        methodEditor.makeTargetNonFinal(0);
        methodEditor.makeTargetPublic(0);
    }
}
