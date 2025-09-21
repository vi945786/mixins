package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.classtypes.extendertype.*;
import vi.mixin.api.classtypes.mixintype.*;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.transformers.TransformerBuilder;
import vi.mixin.api.transformers.TransformerSupplier;

import java.util.List;

public class ShadowTransformer implements TransformerSupplier {

    private static void validateField(AnnotatedFieldEditor mixinEditor, TargetFieldEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        FieldNode mixinFieldNode = mixinEditor.getFieldNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        String name = "@Shadow " + mixinClassNodeClone.name + "." + mixinFieldNode.name;
        if ((targetFieldNode.access & ACC_STATIC) != (mixinFieldNode.access & ACC_STATIC))
            throw new MixinFormatException(name, "should be " + ((targetFieldNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type type = Type.getType(mixinFieldNode.desc);
        if (!type.equals(Type.getType(targetFieldNode.desc)) && !type.equals(Type.getType(Object.class)))
            throw new MixinFormatException(name, "valid types are: " + targetFieldNode.desc + ", " + Type.getType(Object.class));
    }

    private static void validateMethod(AnnotatedMethodEditor mixinEditor, TargetMethodEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        String name = "@Shadow " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;

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
                throw new MixinFormatException(name, "valid types for argument number " + i + " are: " + targetArgumentTypes[i] + ", " + Type.getType(Object.class));
        }
    }

    private static void mixinTransformField(MixinAnnotatedFieldEditor mixinEditor, MixinTargetFieldEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validateField(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        FieldNode mixinFieldNode = mixinEditor.getFieldNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        if ((mixinFieldNode.access & ACC_FINAL) == 0) targetEditor.makeNonFinal();
        targetEditor.makePublic();
        mixinEditor.doNotCopyToTargetClass();

        boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;
        mixinEditor.changeSet(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
        mixinEditor.changeGet(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
    }

    private static void mixinTransformMethod(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validateMethod(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        mixinEditor.delete();
        mixinEditor.doNotCopyToTargetClass();

        int invokeOpcode = INVOKEVIRTUAL;
        if ((targetMethodNode.access & ACC_STATIC) != 0) invokeOpcode = INVOKESTATIC;

        mixinEditor.changeInvoke(new MethodInsnNode(invokeOpcode, targetClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
    }

    private static void extenderTransformField(ExtenderAnnotatedFieldEditor mixinEditor, ExtenderTargetFieldEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validateField(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        FieldNode mixinFieldNode = mixinEditor.getFieldNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        if ((mixinFieldNode.access & ACC_FINAL) == 0) targetEditor.makeNonFinal();
        targetEditor.makePublic();

        mixinEditor.delete();

        boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;
        mixinEditor.changeSet(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
        mixinEditor.changeGet(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
    }

    private static void extenderTransformMethod(ExtenderAnnotatedMethodEditor mixinEditor, ExtenderTargetMethodEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validateMethod(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        mixinEditor.delete();

        int invokeOpcode = INVOKEVIRTUAL;
        if ((targetMethodNode.access & ACC_STATIC) != 0) invokeOpcode = INVOKESTATIC;

        mixinEditor.changeInvoke(new MethodInsnNode(invokeOpcode, targetClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.annotatedFieldTransformerBuilder(MixinMixinClassType.class, Shadow.class).withFieldTarget().setTransformer(ShadowTransformer::mixinTransformField).build(),
                TransformerBuilder.annotatedMethodTransformerBuilder(MixinMixinClassType.class, Shadow.class).withMethodTarget().setTransformer(ShadowTransformer::mixinTransformMethod).build(),
                TransformerBuilder.annotatedFieldTransformerBuilder(ExtenderMixinClassType.class, Shadow.class).withFieldTarget().setTransformer(ShadowTransformer::extenderTransformField).build(),
                TransformerBuilder.annotatedMethodTransformerBuilder(ExtenderMixinClassType.class, Shadow.class).withMethodTarget().setTransformer(ShadowTransformer::extenderTransformMethod).build()
        );
    }

}
