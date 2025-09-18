package vi.mixin.api.transformers;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.MixinFormatException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public non-sealed interface FieldTransformer<ME extends MethodEditor, FE extends FieldEditor, A extends Annotation> extends InnerElementTransformer<ME, FE, A> {

    default TargetType getFieldTargetType() {
        return TargetType.FIELD;
    }
}
