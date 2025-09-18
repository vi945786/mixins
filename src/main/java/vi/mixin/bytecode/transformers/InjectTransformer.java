package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Inject;
import vi.mixin.api.injection.Returner;
import vi.mixin.api.injection.ValueReturner;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.mixintype.MixinMethodEditor;
import vi.mixin.api.transformers.mixintype.MixinMethodTransformer;
import vi.mixin.api.transformers.targeteditors.TargetInsnListEditor;

public class InjectTransformer implements MixinMethodTransformer<Inject> {

    private static void validate(MixinMethodEditor methodEditor, Inject annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = methodEditor.getMixinMethodNodeClone();

        String name = "@Invoker " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if(methodEditor.getNumberOfTargets() != 1) throw new MixinFormatException(name, "illegal number of targets, should be 1");
        MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

        if((targetMethodNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((targetMethodNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        if(!Type.getReturnType(mixinMethodNode.desc).equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "should return void");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length != targetArgumentTypes.length+1) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length+1 + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + (i+1) + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
        Type returnerType;
        if(Type.getReturnType(targetMethodNode.desc).equals(Type.VOID_TYPE)) returnerType = Type.getType(Returner.class);
        else returnerType = Type.getType(ValueReturner.class);
        if (!mixinArgumentTypes[targetArgumentTypes.length].equals(returnerType)) throw new MixinFormatException(name, "valid types for argument number " + (targetArgumentTypes.length+1) + " are: " + returnerType);
    }

    @Override
    public void transform(MixinMethodEditor methodEditor, Inject annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(methodEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        MethodNode mixinMethodNode = methodEditor.getMixinMethodNodeClone();
        MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

        methodEditor.makeTargetPublic(0);

        Type returnType = Type.getReturnType(targetMethodNode.desc);
        boolean isStatic = (targetMethodNode.access & ACC_STATIC) != 0;
        String returner = returnType.getSort() == 0 ? "vi/mixin/api/injection/Returner" : "vi/mixin/api/injection/ValueReturner";

        InsnList insnList = new InsnList();
        insnList.add(new TypeInsnNode(NEW, returner));
        insnList.add(new InsnNode(DUP));
        insnList.add(new MethodInsnNode(INVOKESPECIAL, returner, "<init>", "()V"));
        insnList.add(new InsnNode(DUP));

        if(!isStatic) {
            insnList.add(new VarInsnNode(ALOAD, 0));
            insnList.add(new InsnNode(SWAP));
        }
        int[] loadOpcodes = TransformerHelper.getLoadOpcodes(Type.getArgumentTypes(targetMethodNode.desc));

        for (int i = 0; i < loadOpcodes.length; i++) {
            insnList.add(new VarInsnNode(loadOpcodes[i], i + (isStatic ? 0 : 1)));
            insnList.add(new InsnNode(SWAP));
        }

        insnList.add(new MethodInsnNode(INVOKESTATIC, mixinClassNodeClone.name, mixinMethodNode.name, methodEditor.getUpdatedDesc(targetClassNodeClone.name)));

        LabelNode skipReturn = new LabelNode();
        if(returnType.getSort() != 0) insnList.add(new InsnNode(DUP));
        insnList.add(new MethodInsnNode(INVOKEVIRTUAL, returner, "isReturned", "()Z"));
        insnList.add(new JumpInsnNode(IFEQ, skipReturn));
        if(returnType.getSort() != 0) {

            String methodName = "getReturnValue";
            String methodDesc = "()java/lang/Object";
            if (returnType.getSort() <= 8) {
                methodName += returnType.getInternalName();
                methodDesc = "()" + returnType.getInternalName();
            }
            insnList.add(new MethodInsnNode(INVOKEVIRTUAL, returner, methodName, methodDesc));
        }

        int returnOpcode = TransformerHelper.getReturnOpcode(returnType);
        insnList.add(new InsnNode(returnOpcode));
        insnList.add(skipReturn);
        if(returnType.getSort() != 0) insnList.add(new InsnNode(POP));

        TargetInsnListEditor insnListEditor = methodEditor.getTargetInsnListEditor(0);
        for(int index : TransformerHelper.getAtTargetIndexes(insnListEditor.getInsnListClone(), annotation.at())) {
            insnListEditor.insertBefore(index, insnList);
        }
    }
}
