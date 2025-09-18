package vi.mixin.api.transformers.extendertype;

import vi.mixin.api.transformers.MethodTransformer;

import java.lang.annotation.Annotation;

public interface ExtenderMethodTransformer<A extends Annotation> extends MethodTransformer<ExtenderMethodEditor, ExtenderFieldEditor, A> {

}

