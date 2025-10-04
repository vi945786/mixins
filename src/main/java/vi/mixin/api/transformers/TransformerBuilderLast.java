package vi.mixin.api.transformers;

import java.lang.annotation.Annotation;

public interface TransformerBuilderLast<A extends Annotation, AN, TN> {

    TransformerBuilderLast<A, AN, TN> targetFilter(BuiltTransformer.TargetFilter<A, AN, TN> targetFilter);
    BuiltTransformer build();

}
