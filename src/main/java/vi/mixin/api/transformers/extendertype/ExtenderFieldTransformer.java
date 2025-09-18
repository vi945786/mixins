package vi.mixin.api.transformers.extendertype;

import vi.mixin.api.transformers.FieldTransformer;

import java.lang.annotation.Annotation;

public interface ExtenderFieldTransformer<A extends Annotation> extends FieldTransformer<ExtenderMethodEditor, ExtenderFieldEditor, A> {

}
