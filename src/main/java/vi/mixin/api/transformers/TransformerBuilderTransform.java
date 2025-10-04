package vi.mixin.api.transformers;

import vi.mixin.api.editors.*;

import java.lang.annotation.Annotation;

public interface TransformerBuilderTransform<A extends Annotation, AE extends AnnotatedEditor, TE extends TargetEditor, AN, TN> {

    TransformerBuilderLast<A, AN, TN> transformFunction(BuiltTransformer.TransformFunction<A, AE, TE> transform);

}
