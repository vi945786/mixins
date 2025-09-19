package vi.mixin.api.transformers.annotatedmethod;

import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.MixinClassType;

import java.lang.annotation.Annotation;

public class AnnotatedMethodTransformerBuilder<A extends Annotation, AM extends AnnotatedMethodEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor> {
    private final Class<? extends MixinClassType<?, AM, ?, TM, TF>> mixinClassType;
    private final Class<A> annotation;

    public AnnotatedMethodTransformerBuilder(Class<? extends MixinClassType<?, AM, ?, TM, TF>> mixinClassType, Class<A> annotation) {
        this.mixinClassType = mixinClassType;
        this.annotation = annotation;
    }

    public AnnotatedMethodTargetMethodTransformerBuilder<A, AM, TM> withMethodTarget() {
        return new AnnotatedMethodTargetMethodTransformerBuilder<>(mixinClassType, annotation);
    }

    public AnnotatedMethodTargetFieldTransformerBuilder<A, AM, TF> withFieldTarget() {
        return new AnnotatedMethodTargetFieldTransformerBuilder<>(mixinClassType, annotation);
    }
}
