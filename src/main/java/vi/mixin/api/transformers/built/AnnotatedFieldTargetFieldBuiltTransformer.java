package vi.mixin.api.transformers.built;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.transformers.BuiltTransformer;

import java.lang.annotation.Annotation;

public record AnnotatedFieldTargetFieldBuiltTransformer<A extends Annotation, AF extends AnnotatedFieldEditor, TF extends TargetFieldEditor>
        (Class<? extends MixinClassType<?, ?, AF, ?, TF>> mixinClassType, Class<A> annotation, TransformAnnotatedFieldTargetField<A, AF, TF> transformer, AnnotatedFieldTargetFieldTargetFilter<A> targetFilter) implements BuiltTransformer {

    @Override
    public Class<? extends Annotation> getAnnotation() {
        return annotation();
    }

    @Override
    public Class<? extends MixinClassType> getMixinClassType() {
        return mixinClassType();
    }

    public boolean isAnnotatedMethod() {
        return false;
    }

    public boolean isTargetMethod() {
        return false;
    }

    @Override
    public boolean isTarget(Object mixinFieldNodeClone, Object targetFieldNodeClone, Annotation annotation) {
        return targetFilter.isTarget((FieldNode) mixinFieldNodeClone, (FieldNode) targetFieldNodeClone, (A) annotation);
    }

    @Override
    public void transform(Object mixinEditor, Object targetEditor, Annotation annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        transformer().transform((AF) mixinEditor, (TF) targetEditor, (A) annotation, mixinClassNodeClone, targetClassNodeClone);
    }

    @FunctionalInterface
    public interface TransformAnnotatedFieldTargetField<A extends Annotation, AF extends AnnotatedFieldEditor, TF extends TargetFieldEditor> {
        void transform(AF mixinEditor, TF targetEditor, A annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone);
    }

    @FunctionalInterface
    public interface AnnotatedFieldTargetFieldTargetFilter<A extends Annotation> {
        boolean isTarget(FieldNode mixinFieldNodeClone, FieldNode targetFieldNodeClone, A annotation);
    }
}
