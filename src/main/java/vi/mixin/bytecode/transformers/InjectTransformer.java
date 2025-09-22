package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Inject;
import vi.mixin.api.classtypes.mixintype.MixinAnnotatedMethodEditor;
import vi.mixin.api.classtypes.mixintype.MixinMixinClassType;
import vi.mixin.api.classtypes.mixintype.MixinTargetMethodEditor;
import vi.mixin.api.injection.Returner;
import vi.mixin.api.injection.ValueReturner;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetInsnListEditor;
import vi.mixin.api.injection.Vars;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.transformers.TransformerBuilder;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.TransformerSupplier;

import java.util.List;

public class InjectTransformer implements TransformerSupplier {

    private static void validate(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, Inject annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        String name = "@Inject " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if((targetMethodNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((targetMethodNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        if(!Type.getReturnType(mixinMethodNode.desc).equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "should return void");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length < targetArgumentTypes.length+1) throw new MixinFormatException(name, "there should be at least " + (targetArgumentTypes.length+1) + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + (i+1) + " are: " + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
        Type returnerType;
        if(Type.getReturnType(targetMethodNode.desc).equals(Type.VOID_TYPE)) returnerType = Type.getType(Returner.class);
        else returnerType = Type.getType(ValueReturner.class);
        if (!mixinArgumentTypes[targetArgumentTypes.length].equals(returnerType)) throw new MixinFormatException(name, "valid types for argument number " + (targetArgumentTypes.length+1) + " are: " + returnerType);
        if(mixinArgumentTypes.length > targetArgumentTypes.length+2) throw new MixinFormatException(name, "illegal number of parameters");
        if(mixinArgumentTypes.length == targetArgumentTypes.length+2 && !mixinArgumentTypes[targetArgumentTypes.length+1].equals(Type.getType(Vars.class))) throw new MixinFormatException(name, "non Vars parameter after Returner");
    }

    private static void transform(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, Inject annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        mixinEditor.doNotCopyToTargetClass();
        mixinEditor.makePublic();
        targetEditor.makePublic();

        Type returnType = Type.getReturnType(targetMethodNode.desc);
        boolean isStatic = (targetMethodNode.access & ACC_STATIC) != 0;
        String returner = returnType.getSort() == 0 ? Type.getInternalName(Returner.class) : Type.getInternalName(ValueReturner.class);

        MixinClassTargetInsnListEditor insnListEditor = targetEditor.getInsnListEditor();
        for (int atIndex : TransformerHelper.getAtTargetIndexesThrows(insnListEditor.getInsnListClone(), annotation.at(), "@Inject " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc)) {
            InsnList insnList = new InsnList();
            insnList.add(new TypeInsnNode(NEW, returner));

            int opcode = insnListEditor.getInsnListClone().get(atIndex).getOpcode();
            if(IRETURN <= opcode && opcode <= ARETURN) {
                insnList.add(new InsnNode(DUP_X1));
                insnList.add(new InsnNode(SWAP));
                insnList.add(new InsnNode(DUP_X2));
                insnList.add(new MethodInsnNode(INVOKESPECIAL, returner, "<init>", "(" + Type.getReturnType(targetMethodNode.desc).getDescriptor() + ")V"));
            } else {
                insnList.add(new InsnNode(DUP));
                insnList.add(new MethodInsnNode(INVOKESPECIAL, returner, "<init>", "()V"));
            }
            insnList.add(new InsnNode(DUP));

            if (!isStatic) {
                insnList.add(new VarInsnNode(ALOAD, 0));
                insnList.add(new InsnNode(SWAP));
            }
            int[] loadOpcodes = TransformerHelper.getLoadOpcodes(Type.getArgumentTypes(targetMethodNode.desc));

            for (int i = 0; i < loadOpcodes.length; i++) {
                insnList.add(new VarInsnNode(loadOpcodes[i], i + (isStatic ? 0 : 1)));
                insnList.add(new InsnNode(SWAP));
            }

            if (Type.getArgumentCount(mixinMethodNode.desc) == Type.getArgumentCount(targetMethodNode.desc) + 2)
                insnList.add(targetEditor.getCaptureLocalsInsnList(atIndex, targetClassNodeClone.name));

            insnList.add(new MethodInsnNode(INVOKESTATIC, mixinClassNodeClone.name, mixinMethodNode.name, mixinEditor.getUpdatedDesc(targetClassNodeClone.name)));

            LabelNode skipReturn = new LabelNode();
            if (returnType.getSort() != 0) insnList.add(new InsnNode(DUP));
            insnList.add(new MethodInsnNode(INVOKEVIRTUAL, returner, "isReturned", "()Z"));
            insnList.add(new JumpInsnNode(IFEQ, skipReturn));
            if (returnType.getSort() != 0) {

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
            if (returnType.getSort() != 0) insnList.add(new InsnNode(POP));

            insnListEditor.insertBefore(atIndex, insnList);
        }
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.annotatedMethodTransformerBuilder(MixinMixinClassType.class, Inject.class).withMethodTarget().setTransformer(InjectTransformer::transform).build()
        );
    }
}
