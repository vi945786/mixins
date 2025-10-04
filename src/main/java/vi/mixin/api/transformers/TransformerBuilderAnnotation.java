package vi.mixin.api.transformers;

import vi.mixin.api.editors.*;

import java.lang.annotation.Annotation;

public interface TransformerBuilderAnnotation<AM extends AnnotatedMethodEditor, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor> {

    <A extends Annotation> TransformerBuilderAnnotated<A, AM, AF, TM, TF> annotation(Class<A> annotation);

}
