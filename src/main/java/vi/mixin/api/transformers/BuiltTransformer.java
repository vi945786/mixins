package vi.mixin.api.transformers;

import org.objectweb.asm.tree.ClassNode;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.editors.*;

import java.lang.annotation.Annotation;

public record BuiltTransformer
        (Class<? extends MixinClassType<?, ?, ?, ? , ?>> mixinClassType,
         Class<? extends Annotation> annotation,
         boolean isAnnotatedMethod,
         boolean isTargetMethod,
         TargetFilter<Annotation, Object, Object> targetFilter,
         TransformFunction<Annotation, AnnotatedEditor, TargetEditor> transformFunction,
         boolean allowTargetInSuper) {

    @FunctionalInterface
    public interface TargetFilter<A extends Annotation, AN, TN> {
        boolean isTarget(AN annotatedNodeClone, TN targetNodeClone, A annotation, ClassNode origin);
    }

    @FunctionalInterface
    public interface TransformFunction<A extends Annotation, AE extends AnnotatedEditor, TE extends TargetEditor> {
        void transform(AE annotatedEditor, TE targetEditor, A annotation, ClassNode annotatedClassNodeClone, ClassNode targetOriginClassNodeClone);
    }
}
