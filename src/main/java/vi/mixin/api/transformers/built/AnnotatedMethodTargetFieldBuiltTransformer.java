package vi.mixin.api.transformers.built;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.transformers.BuiltTransformer;

import java.lang.annotation.Annotation;

public record AnnotatedMethodTargetFieldBuiltTransformer<A extends Annotation, AM extends AnnotatedMethodEditor, TF extends TargetFieldEditor>
        (Class<? extends MixinClassType<?, AM, ?, ?, TF>> mixinClassType, Class<A> annotation, TransformAnnotatedMethodTargetField<A, AM, TF> transformer, AnnotatedMethodTargetFieldTargetFilter<A> targetFilter) implements BuiltTransformer {

    @Override
    public Class<? extends Annotation> getAnnotation() {
        return annotation();
    }

    @Override
    public Class<? extends MixinClassType<?, ?, ?, ?, ?>> getMixinClassType() {
        return mixinClassType();
    }

    public boolean isAnnotatedMethod() {
        return true;
    }

    public boolean isTargetMethod() {
        return false;
    }

    @Override @SuppressWarnings("unchecked")
    public boolean isTarget(Object mixinFieldNodeClone, Object targetFieldNodeClone, Annotation annotation) {
        return targetFilter.isTarget((MethodNode) mixinFieldNodeClone, (FieldNode) targetFieldNodeClone, (A) annotation);
    }

    @Override @SuppressWarnings("unchecked")
    public void transform(Object mixinEditor, Object targetEditor, Annotation annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        transformer().transform((AM) mixinEditor, (TF) targetEditor, (A) annotation, mixinClassNodeClone, targetClassNodeClone);
    }

    @FunctionalInterface
    public interface TransformAnnotatedMethodTargetField<A extends Annotation, AM extends AnnotatedMethodEditor, TF extends TargetFieldEditor> {
        void transform(AM mixinEditor, TF targetEditor, A annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone);
    }

    @FunctionalInterface
    public interface AnnotatedMethodTargetFieldTargetFilter<A extends Annotation> {
        boolean isTarget(MethodNode mixinMethodNodeClone, FieldNode targetFieldNodeClone, A annotation);
    }
}
