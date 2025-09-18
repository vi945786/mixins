package vi.mixin.api.transformers.accessortype;

import vi.mixin.api.transformers.MethodTransformer;

import java.lang.annotation.Annotation;

public interface AccessorMethodTransformer<A extends Annotation> extends MethodTransformer<AccessorMethodEditor, AccessorFieldEditor, A> {


}

