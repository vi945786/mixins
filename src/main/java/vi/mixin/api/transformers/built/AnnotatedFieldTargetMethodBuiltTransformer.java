package vi.mixin.api.transformers.built;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.transformers.BuiltTransformer;

import java.lang.annotation.Annotation;

public record AnnotatedFieldTargetMethodBuiltTransformer<A extends Annotation, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor>
        (Class<? extends MixinClassType<?, ?, AF, TM, ?>> mixinClassType, Class<A> annotation, TransformAnnotatedFieldTargetMethod<A, AF, TM> transformer, AnnotatedFieldTargetMethodTargetFilter<A> targetFilter) implements BuiltTransformer {

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
        return true;
    }

    @Override
    public boolean isTarget(Object mixinFieldNodeClone, Object targetFieldNodeClone, Annotation annotation) {
        return targetFilter.isTarget((FieldNode) mixinFieldNodeClone, (MethodNode) targetFieldNodeClone, (A) annotation);
    }

    @Override
    public void transform(Object mixinEditor, Object targetEditor, Annotation annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        transformer().transform((AF) mixinEditor, (TM) targetEditor, (A) annotation, mixinClassNodeClone, targetClassNodeClone);
    }

    @FunctionalInterface
    public interface TransformAnnotatedFieldTargetMethod<A extends Annotation, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor> {
        void transform(AF mixinEditor, TM targetEditor, A annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone);
    }

    @FunctionalInterface
    public interface AnnotatedFieldTargetMethodTargetFilter<A extends Annotation> {
        boolean isTarget(FieldNode mixinFieldNodeClone, MethodNode targetMethodNodeClone, A annotation);
    }
}
