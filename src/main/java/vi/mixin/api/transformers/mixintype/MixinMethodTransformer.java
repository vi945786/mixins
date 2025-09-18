package vi.mixin.api.transformers.mixintype;

import vi.mixin.api.transformers.MethodTransformer;

import java.lang.annotation.Annotation;

public interface MixinMethodTransformer<A extends Annotation> extends MethodTransformer<MixinMethodEditor, MixinFieldEditor, A> {

}

