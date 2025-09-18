package vi.mixin.api.transformers.mixintype;

import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.transformers.FieldTransformer;

import java.lang.annotation.Annotation;

public interface MixinFieldTransformer<A extends Annotation> extends FieldTransformer<MixinMethodEditor, MixinFieldEditor, A> {

}
