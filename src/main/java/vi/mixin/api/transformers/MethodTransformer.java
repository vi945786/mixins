package vi.mixin.api.transformers;

import java.lang.annotation.Annotation;

public non-sealed interface MethodTransformer<ME extends MethodEditor, FE extends FieldEditor, A extends Annotation> extends InnerElementTransformer<ME, FE, A> {

    default TargetType getTargetMethodType() {
        return TargetType.METHOD;
    }
}
