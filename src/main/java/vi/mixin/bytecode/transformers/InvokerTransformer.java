package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Invoker;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.accessortype.AccessorMethodEditor;
import vi.mixin.api.transformers.accessortype.AccessorMethodTransformer;

import static vi.mixin.api.transformers.TransformerHelper.addLoadOpcodesOfMethod;

public class InvokerTransformer implements AccessorMethodTransformer<Invoker> {

    private static void validate(AccessorMethodEditor methodEditor, Invoker annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = methodEditor.getMixinMethodNodeClone();

        String name = "@Invoker " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if(methodEditor.getNumberOfTargets() != 1) throw new MixinFormatException(name, "illegal number of targets, should be 1");
        MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

        if(targetMethodNode.name.equals("<init>")) throw new MixinFormatException(name, "invoking a constructor is not allowed. use @New");
        if((targetMethodNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((targetMethodNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.equals(Type.getReturnType(targetMethodNode.desc)) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + Type.getReturnType(targetMethodNode.desc) + ", " + Type.getType(Object.class));
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
    }

    public void transform(AccessorMethodEditor methodEditor, Invoker annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = methodEditor.getMixinMethodNodeClone();
        MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

        methodEditor.makeTargetPublic(0);

        if(!targetMethodNode.name.equals(mixinMethodNode.name) /*|| (targetMethodNode.access & ACC_STATIC) != 0*/) {
            boolean isStatic = (targetMethodNode.access & ACC_STATIC) != 0;

            int invokeOpcode = INVOKEVIRTUAL;
            if(isStatic) invokeOpcode = INVOKESTATIC;
            else if((targetClassNodeClone.access & ACC_INTERFACE) != 0) invokeOpcode = INVOKEINTERFACE;

            int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(mixinMethodNode.desc));

            InsnList insnList = new InsnList();
            addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(mixinMethodNode.desc), isStatic);

            insnList.add(new MethodInsnNode(invokeOpcode, targetClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
            insnList.add(new InsnNode(returnOpcode));
            methodEditor.setMixinBytecode(insnList);
        }
    }
}
