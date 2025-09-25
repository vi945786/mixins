package vi.mixin.api.transformers.annotatedfield;

import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.MixinClassType;

import java.lang.annotation.Annotation;

public class AnnotatedFieldTransformerBuilder<A extends Annotation, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor> {
    private final Class<? extends MixinClassType<?, ?, AF, TM, TF>> mixinClassType;
    private final Class<A> annotation;

    public AnnotatedFieldTransformerBuilder(Class<? extends MixinClassType<?, ?, AF, TM, TF>> mixinClassType, Class<A> annotation) {
        this.mixinClassType = mixinClassType;
        this.annotation = annotation;
    }

    @SuppressWarnings("unused")
    public AnnotatedFieldTargetMethodTransformerBuilder<A, AF, TM> withMethodTarget() {
        return new AnnotatedFieldTargetMethodTransformerBuilder<>(mixinClassType, annotation);
    }

    public AnnotatedFieldTargetFieldTransformerBuilder<A, AF, TF> withFieldTarget() {
        return new AnnotatedFieldTargetFieldTransformerBuilder<>(mixinClassType, annotation);
    }
}
