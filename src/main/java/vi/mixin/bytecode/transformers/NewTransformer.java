package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.transformers.MethodEditor;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.accessortype.AccessorMethodEditor;
import vi.mixin.api.transformers.accessortype.AccessorMethodTransformer;
import vi.mixin.api.transformers.extendertype.ExtenderMethodEditor;
import vi.mixin.api.transformers.extendertype.ExtenderMethodTransformer;
import vi.mixin.api.transformers.mixintype.MixinMethodEditor;
import vi.mixin.api.transformers.mixintype.MixinMethodTransformer;

import static org.objectweb.asm.Opcodes.ACC_STATIC;

public class NewTransformer {

    private static void validate(MethodEditor methodEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = methodEditor.getMixinMethodNodeClone();

        String name = "@New " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if(methodEditor.getNumberOfTargets() != 1) throw new MixinFormatException(name, "illegal number of targets, should be 1");
        MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

        if((mixinMethodNode.access & ACC_STATIC) == 0) throw new MixinFormatException(name, "should be static");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.getInternalName().equals(targetClassNodeClone.name) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + "L" + targetClassNodeClone.name + ";" + ", " + Type.getType(Object.class));
    }

    private static boolean isMethodTarget(MethodNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, New annotation) {
        if(annotation.value().isEmpty()) {
            return targetMethodNodeClone.name.equals("<init>") && targetMethodNodeClone.desc.split("\\)")[0].equals(mixinMethodNodeClone.desc.split("\\)")[0]);
        }
        return targetMethodNodeClone.name.equals("<init>") && targetMethodNodeClone.desc.equals("(" + annotation.value() + ")V");
    }

    public static class NewMixinTransformer implements MixinMethodTransformer<New> {
        public boolean isMethodTarget(MethodNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, New annotation) {
            return NewTransformer.isMethodTarget(mixinMethodNodeClone, targetMethodNodeClone, annotation);
        }

        @Override
        public void transform(MixinMethodEditor methodEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
            validate(methodEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
            methodEditor.doNotCopyToTarget();
            MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

            methodEditor.makeTargetPublic(0);

            InsnList insnList = new InsnList();
            insnList.add(new TypeInsnNode(NEW, targetClassNodeClone.name));
            insnList.add(new InsnNode(DUP));

            TransformerHelper.addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(targetMethodNode.desc), true);

            insnList.add(new MethodInsnNode(INVOKESPECIAL, targetClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
            insnList.add(new InsnNode(ARETURN));

            methodEditor.setMixinBytecode(insnList);
        }
    }

    public static class NewAccessorTransformer implements AccessorMethodTransformer<New> {
        public boolean isMethodTarget(MethodNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, New annotation) {
            return NewTransformer.isMethodTarget(mixinMethodNodeClone, targetMethodNodeClone, annotation);
        }

        @Override
        public void transform(AccessorMethodEditor methodEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
            MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

            methodEditor.makeTargetPublic(0);

            InsnList insnList = new InsnList();
            insnList.add(new TypeInsnNode(NEW, targetClassNodeClone.name));
            insnList.add(new InsnNode(DUP));

            TransformerHelper.addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(targetMethodNode.desc), true);

            insnList.add(new MethodInsnNode(INVOKESPECIAL, targetClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
            insnList.add(new InsnNode(ARETURN));

            methodEditor.setMixinBytecode(insnList);
        }
    }

    public static class NewExtenderTransformer implements ExtenderMethodTransformer<New> {
        public boolean isMethodTarget(MethodNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, New annotation) {
            return NewTransformer.isMethodTarget(mixinMethodNodeClone, targetMethodNodeClone, annotation);
        }

        private static void validate(ExtenderMethodEditor methodEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
            MethodNode mixinMethodNode = methodEditor.getMixinMethodNodeClone();

            String name = "@New " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
            if(methodEditor.getNumberOfTargets() != 1) throw new MixinFormatException(name, "illegal number of targets, should be 1");
            MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

            if((mixinMethodNode.access & ACC_STATIC) == 0) throw new MixinFormatException(name, "should be static");

            Type returnType = Type.getReturnType(mixinMethodNode.desc);
            if(!returnType.equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "valid return types are: void");
        }

        @Override
        public void transform(ExtenderMethodEditor methodEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
            methodEditor.makeTargetPublic(0);
            methodEditor.delete();
        }
    }
}
