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
        /**
         * @param annotatedNodeClone a clone of the MethodNode/FieldNode that has the annotation.
         * @param targetNodeClone a clone of the MethodNode/FieldNode that might be the target of the annotatedNodeClone.
         * @param annotation the annotation applied to the annotated method/field.
         * @param origin a clone of the ClassNode that defined targetNodeClone.
         * @return whether the targetNodeClone parameter is the target
         */
        boolean isTarget(AN annotatedNodeClone, TN targetNodeClone, A annotation, ClassNode origin);
    }

    @FunctionalInterface
    public interface TransformFunction<A extends Annotation, AE extends AnnotatedEditor, TE extends TargetEditor> {
        /**
         * @param annotatedEditor the annotated method/field editor defined by the MixinClassType of this BuiltTransformer.
         * @param targetEditor the target method/field editor defined by the MixinClassType of this BuiltTransformer.
         * @param annotation the annotation applied to the annotated method/field.
         * @param annotatedClassNodeClone a clone of the mixin class's ClassNode.
         * @param targetOriginClassNodeClone a clone of the ClassNode that defined the target method/field.
         */
        void transform(AE annotatedEditor, TE targetEditor, A annotation, ClassNode annotatedClassNodeClone, ClassNode targetOriginClassNodeClone);
    }
}
