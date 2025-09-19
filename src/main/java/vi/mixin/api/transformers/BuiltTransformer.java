package vi.mixin.api.transformers;

import org.objectweb.asm.tree.ClassNode;
import vi.mixin.api.classtypes.MixinClassType;

import java.lang.annotation.Annotation;

public interface BuiltTransformer {
    Class<? extends Annotation> getAnnotation();
    Class<? extends MixinClassType> getMixinClassType();

    boolean isAnnotatedMethod();
    boolean isTargetMethod();

    boolean isTarget(Object mixinFieldNodeClone, Object targetFieldNodeClone, Annotation annotation);
    void transform(Object mixinEditor, Object targetEditor, Annotation annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone);
}
