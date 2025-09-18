package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.transformers.FieldEditor;
import vi.mixin.api.transformers.MethodEditor;
import vi.mixin.api.transformers.accessortype.AccessorFieldEditor;
import vi.mixin.api.transformers.accessortype.AccessorMethodEditor;
import vi.mixin.api.transformers.accessortype.AccessorMethodTransformer;
import vi.mixin.api.transformers.extendertype.ExtenderFieldEditor;
import vi.mixin.api.transformers.extendertype.ExtenderFieldTransformer;
import vi.mixin.api.transformers.extendertype.ExtenderMethodEditor;
import vi.mixin.api.transformers.extendertype.ExtenderMethodTransformer;
import vi.mixin.api.transformers.mixintype.MixinFieldEditor;
import vi.mixin.api.transformers.mixintype.MixinFieldTransformer;
import vi.mixin.api.transformers.mixintype.MixinMethodEditor;
import vi.mixin.api.transformers.mixintype.MixinMethodTransformer;

import java.lang.reflect.InvocationTargetException;

import static org.objectweb.asm.Opcodes.*;

public class ShadowTransformer {

    private static void validate(FieldEditor fieldEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        FieldNode mixinFieldNode = fieldEditor.getMixinFieldNodeClone();

        String name = "@Shadow " + mixinClassNodeClone.name + "." + mixinFieldNode.name;
        if (fieldEditor.getNumberOfTargets() != 1)
            throw new MixinFormatException(name, "illegal number of targets, should be 1");
        FieldNode targetFieldNode = fieldEditor.getTargetFieldNodeClone(0);

        if ((targetFieldNode.access & ACC_STATIC) != (mixinFieldNode.access & ACC_STATIC))
            throw new MixinFormatException(name, "should be " + ((targetFieldNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type type = Type.getType(mixinFieldNode.desc);
        if (!type.equals(Type.getType(targetFieldNode.desc)) && !type.equals(Type.getType(Object.class)))
            throw new MixinFormatException(name, "valid types are: " + targetFieldNode.desc + ", " + Type.getType(Object.class));
    }

    private static void validate(MethodEditor methodEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = methodEditor.getMixinMethodNodeClone();

        String name = "@Shadow " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if (methodEditor.getNumberOfTargets() != 1)
            throw new MixinFormatException(name, "illegal number of targets, should be 1");
        MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

        if (targetMethodNode.name.equals("<init>"))
            throw new MixinFormatException(name, "shadowing a constructor is not allowed. use @New");
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if (!returnType.equals(Type.getReturnType(targetMethodNode.desc)) && !returnType.equals(Type.getType(Object.class)))
            throw new MixinFormatException(name, "valid return types are: " + Type.getReturnType(targetMethodNode.desc) + ", " + Type.getType(Object.class));
        if ((targetMethodNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC))
            throw new MixinFormatException(name, "should be " + ((targetMethodNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if (mixinArgumentTypes.length != targetArgumentTypes.length)
            throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class)))
                throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " + Type.getType(Object.class));
        }
    }

    public static class ShadowMixinTransformer implements MixinMethodTransformer<Shadow>, MixinFieldTransformer<Shadow> {

        @Override
        public void transform(MixinFieldEditor fieldEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
            validate(fieldEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
            FieldNode mixinFieldNode = fieldEditor.getMixinFieldNodeClone();
            FieldNode targetFieldNode = fieldEditor.getTargetFieldNodeClone(0);

            if ((mixinFieldNode.access & ACC_FINAL) == 0) fieldEditor.makeTargetNonFinal(0);
            fieldEditor.makeTargetPublic(0);

            boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;
            fieldEditor.changeSet(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
            fieldEditor.changeGet(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
        }

        @Override
        public void transform(MixinMethodEditor methodEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
            validate(methodEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
            MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

            methodEditor.delete();
            methodEditor.doNotCopyToTarget();

            int invokeOpcode = INVOKEVIRTUAL;
            if ((targetMethodNode.access & ACC_STATIC) != 0) invokeOpcode = INVOKESTATIC;

            methodEditor.changeInvoke(new MethodInsnNode(invokeOpcode, targetClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
        }
    }

    public static class ShadowExtenderTransformer implements ExtenderMethodTransformer<Shadow>, ExtenderFieldTransformer<Shadow> {

        @Override
        public void transform(ExtenderFieldEditor fieldEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
            validate(fieldEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
            FieldNode mixinFieldNode = fieldEditor.getMixinFieldNodeClone();
            FieldNode targetFieldNode = fieldEditor.getTargetFieldNodeClone(0);

            if ((mixinFieldNode.access & ACC_FINAL) == 0) fieldEditor.makeTargetNonFinal(0);
            fieldEditor.makeTargetPublic(0);

            fieldEditor.delete();

            boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;
            fieldEditor.changeSet(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
            fieldEditor.changeGet(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
        }

        @Override
        public void transform(ExtenderMethodEditor methodEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
            validate(methodEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
            MethodNode targetMethodNode = methodEditor.getTargetMethodNodeClone(0);

            methodEditor.delete();

            int invokeOpcode = INVOKEVIRTUAL;
            if ((targetMethodNode.access & ACC_STATIC) != 0) invokeOpcode = INVOKESTATIC;

            methodEditor.changeInvoke(new MethodInsnNode(invokeOpcode, targetClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
        }
    }
}
