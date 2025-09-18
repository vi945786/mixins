package vi.mixin.api.transformers;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.MixinFormatException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

public sealed interface InnerElementTransformer<ME extends MethodEditor, FE extends FieldEditor, A extends Annotation> extends Transformer permits MethodTransformer, FieldTransformer {
    enum TargetType {
        METHOD,
        FIELD
    }

    default boolean isMethodTarget(MethodNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, A annotation) {
        try {
            String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
            if(value.isEmpty()) {
                return (mixinMethodNodeClone.name + mixinMethodNodeClone.desc).equals(targetMethodNodeClone.name + targetMethodNodeClone.desc);
            } else {
                boolean onlyName = !value.contains("(");
                return (targetMethodNodeClone.name + (onlyName ? "" : targetMethodNodeClone.desc)).equals(value);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
            throw new MixinFormatException(annotation.annotationType().getName(), "custom target value implement InnerElementTransformer.getMethodTargetRefs");
        }
    }

    default boolean isMethodTarget(FieldNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, A annotation) {
        return isMethodTarget((MethodNode) null, targetMethodNodeClone, annotation);
    }

    default boolean isFieldTarget(FieldNode mixinFieldNodeClone, FieldNode targetFieldNodeClone, A annotation) {
        try {
            String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
            if(value.isEmpty()) {
                return (mixinFieldNodeClone.name).equals(targetFieldNodeClone.name);
            } else {
                return targetFieldNodeClone.name.equals(value);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
            throw new MixinFormatException(annotation.annotationType().getName(), "custom target value implement InnerElementTransformer.getMethodTargetRefs");
        }
    }

    default boolean isFieldTarget(MethodNode mixinFieldNodeClone, FieldNode targetFieldNodeClone, A annotation) {
        return isFieldTarget((FieldNode) null, targetFieldNodeClone, annotation);
    }

    default void transform(ME methodEditor, A annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        throw new UnsupportedOperationException();
    }

    default void transform(FE fieldEditor, A annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        throw new UnsupportedOperationException();
    }
}
