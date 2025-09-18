package vi.mixin.api.transformers;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.MixinFormatException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public non-sealed interface MethodTransformer<ME extends MethodEditor, FE extends FieldEditor, A extends Annotation> extends InnerElementTransformer<ME, FE, A> {

    default TargetType getTargetMethodType() {
        return TargetType.METHOD;
    }
}
