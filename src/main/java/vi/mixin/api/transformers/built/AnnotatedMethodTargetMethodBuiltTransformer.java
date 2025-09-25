package vi.mixin.api.transformers.built;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.transformers.BuiltTransformer;

import java.lang.annotation.Annotation;

public record AnnotatedMethodTargetMethodBuiltTransformer<A extends Annotation, AM extends AnnotatedMethodEditor, TM extends TargetMethodEditor>
        (Class<? extends MixinClassType<?, AM, ?, TM, ?>> mixinClassType, Class<A> annotation, TransformAnnotatedMethodTargetMethod<A, AM, TM> transformer, AnnotatedMethodTargetMethodTargetFilter<A> targetFilter) implements BuiltTransformer {

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
        return true;
    }

    @Override @SuppressWarnings("unchecked")
    public boolean isTarget(Object mixinFieldNodeClone, Object targetFieldNodeClone, Annotation annotation) {
        return targetFilter.isTarget((MethodNode) mixinFieldNodeClone, (MethodNode) targetFieldNodeClone, (A) annotation);
    }

    @Override @SuppressWarnings("unchecked")
    public void transform(Object mixinEditor, Object targetEditor, Annotation annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        transformer().transform((AM) mixinEditor, (TM) targetEditor, (A) annotation, mixinClassNodeClone, targetClassNodeClone);
    }

    @FunctionalInterface
    public interface TransformAnnotatedMethodTargetMethod<A extends Annotation, AM extends AnnotatedMethodEditor, TM extends TargetMethodEditor> {
        void transform(AM mixinEditor, TM targetEditor, A annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone);
    }

    @FunctionalInterface
    public interface AnnotatedMethodTargetMethodTargetFilter<A extends Annotation> {
        boolean isTarget(MethodNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, A annotation);
    }
}
