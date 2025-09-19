package vi.mixin.api.transformers;



import vi.mixin.api.transformers.annotatedfield.AnnotatedFieldTransformerBuilder;
import vi.mixin.api.transformers.annotatedmethod.AnnotatedMethodTransformerBuilder;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.MixinClassType;

import java.lang.annotation.Annotation;

public class TransformerBuilder {
    
    public static <A extends Annotation, AM extends AnnotatedMethodEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor>
    AnnotatedMethodTransformerBuilder<A, AM, TM, TF> annotatedMethodTransformerBuilder(Class<? extends MixinClassType<?, AM, ?, TM, TF>> mixinClassType, Class<A> annotation) {
        return new AnnotatedMethodTransformerBuilder<>(mixinClassType, annotation);
    }

    public static <A extends Annotation, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor>
    AnnotatedFieldTransformerBuilder<A, AF, TM, TF> annotatedFieldTransformerBuilder(Class<? extends MixinClassType<?, ?, AF, TM, TF>> mixinClassType, Class<A> annotation) {
        return new AnnotatedFieldTransformerBuilder<>(mixinClassType, annotation);
    }
}
