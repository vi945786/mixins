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
import vi.mixin.api.util.TransformerHelper;

import java.util.List;

@SuppressWarnings("unused")
public class ShadowTransformer implements TransformerSupplier {

    private static void validateField(AnnotatedFieldEditor mixinEditor, TargetFieldEditor targetEditor, ClassNode mixinClassNodeClone) {
        FieldNode mixinFieldNode = mixinEditor.getFieldNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        String name = "@Shadow " + mixinClassNodeClone.name + "." + mixinFieldNode.name;
        if ((targetFieldNode.access & ACC_STATIC) != (mixinFieldNode.access & ACC_STATIC))
            throw new MixinFormatException(name, "should be " + ((targetFieldNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type type = Type.getType(mixinFieldNode.desc);
        if (!type.equals(Type.getType(targetFieldNode.desc)) && !type.equals(Type.getType(Object.class)))
            throw new MixinFormatException(name, "valid types are: " + targetFieldNode.desc + ", " + Type.getType(Object.class));
    }

    private static void validateMethod(AnnotatedMethodEditor mixinEditor, TargetMethodEditor targetEditor, ClassNode mixinClassNodeClone) {
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
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && (!mixinArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE))
                throw new MixinFormatException(name, "valid types for argument number " + (i+1) + " are: " + targetArgumentTypes[i] + (targetArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE ? "" : ", " + Type.getType(Object.class)));
        }
    }

    private static void mixinTransformField(MixinAnnotatedFieldEditor mixinEditor, MixinTargetFieldEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetOriginClassNodeClone) {
        validateField(mixinEditor, targetEditor, mixinClassNodeClone);
        FieldNode mixinFieldNode = mixinEditor.getFieldNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        if ((mixinFieldNode.access & ACC_FINAL) == 0) targetEditor.makeNonFinal();
        targetEditor.makePublic();
        mixinEditor.doNotCopyToTargetClass();

        boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;
        String updatedName = targetFieldNode.name;

        if(TransformerHelper.getTargetClassName(targetOriginClassNodeClone) != null) {
            updatedName = MixinMixinClassType.getReplaceName(targetOriginClassNodeClone.name) + updatedName;
        }
        mixinEditor.changeSet(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, mixinEditor.getRealTargetClassName(), updatedName, targetFieldNode.desc));
        mixinEditor.changeGet(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, mixinEditor.getRealTargetClassName(), updatedName, targetFieldNode.desc));
    }

    private static void mixinTransformMethod(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetOriginClassNodeClone) {
        validateMethod(mixinEditor, targetEditor, mixinClassNodeClone);
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        mixinEditor.delete();
        mixinEditor.doNotCopyToTargetClass();
        targetEditor.makePublic();

        int invokeOpcode = INVOKESPECIAL;
        if ((targetMethodNode.access & ACC_STATIC) != 0) invokeOpcode = INVOKESTATIC;
        String updatedDesc = targetMethodNode.desc;

        if(TransformerHelper.getTargetClassName(targetOriginClassNodeClone) != null) {
            if(invokeOpcode != INVOKESTATIC) updatedDesc = MixinMixinClassType.getNewDesc(mixinMethodNode.desc, mixinEditor.getRealTargetClassName());
            invokeOpcode = INVOKESTATIC;
        }
        mixinEditor.changeInvoke(new MethodInsnNode(invokeOpcode, targetOriginClassNodeClone.name, targetMethodNode.name, updatedDesc, (targetOriginClassNodeClone.access & ACC_INTERFACE) != 0));
    }

    private static void extenderTransformField(ExtenderAnnotatedFieldEditor mixinEditor, ExtenderTargetFieldEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetOriginClassNodeClone) {
        validateField(mixinEditor, targetEditor, mixinClassNodeClone);
        FieldNode mixinFieldNode = mixinEditor.getFieldNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        if ((mixinFieldNode.access & ACC_FINAL) == 0) targetEditor.makeNonFinal();
        targetEditor.makePublic();

        mixinEditor.delete();

        boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;
        mixinEditor.changeSet(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, targetOriginClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
        mixinEditor.changeGet(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, targetOriginClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
    }

    private static void extenderTransformMethod(ExtenderAnnotatedMethodEditor mixinEditor, ExtenderTargetMethodEditor targetEditor, Shadow annotation, ClassNode mixinClassNodeClone, ClassNode targetOriginClassNodeClone) {
        validateMethod(mixinEditor, targetEditor, mixinClassNodeClone);
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        mixinEditor.delete();
        targetEditor.makePublic();

        int invokeOpcode = INVOKESPECIAL;
        if ((targetMethodNode.access & ACC_STATIC) != 0) invokeOpcode = INVOKESTATIC;

        mixinEditor.changeInvoke(new MethodInsnNode(invokeOpcode, targetOriginClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc, (targetOriginClassNodeClone.access & ACC_INTERFACE) != 0));
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.getTransformerBuilder(MixinMixinClassType.class).annotation(Shadow.class).annotatedField().targetField().transformFunction(ShadowTransformer::mixinTransformField).allowTargetsInSuper().build(),
                TransformerBuilder.getTransformerBuilder(MixinMixinClassType.class).annotation(Shadow.class).annotatedMethod().targetMethod().transformFunction(ShadowTransformer::mixinTransformMethod).allowTargetsInSuper().build(),
                TransformerBuilder.getTransformerBuilder(ExtenderMixinClassType.class).annotation(Shadow.class).annotatedField().targetField().transformFunction(ShadowTransformer::extenderTransformField).allowTargetsInSuper().build(),
                TransformerBuilder.getTransformerBuilder(ExtenderMixinClassType.class).annotation(Shadow.class).annotatedMethod().targetMethod().transformFunction(ShadowTransformer::extenderTransformMethod).allowTargetsInSuper().build()
        );
    }

}
