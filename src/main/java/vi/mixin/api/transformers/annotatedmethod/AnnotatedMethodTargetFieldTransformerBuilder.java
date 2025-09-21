package vi.mixin.api.transformers.annotatedmethod;

import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.transformers.built.AnnotatedMethodTargetFieldBuiltTransformer;
import vi.mixin.api.classtypes.MixinClassType;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

import static vi.mixin.api.transformers.built.AnnotatedMethodTargetFieldBuiltTransformer.*;

public class AnnotatedMethodTargetFieldTransformerBuilder<A extends Annotation, AM extends AnnotatedMethodEditor, TF extends TargetFieldEditor> {
    private final Class<? extends MixinClassType<?, AM, ?, ?, TF>> mixinClassType;
    private final Class<A> annotation;

    private TransformAnnotatedMethodTargetField<A, AM, TF> transformer;
    private AnnotatedMethodTargetFieldTargetFilter<A> targetFilter = (mixinMethodNodeClone, targetFieldNodeClone, annotation) -> {
        try {
            String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
            if(value.isEmpty()) {
                return mixinMethodNodeClone.name.equals(targetFieldNodeClone.name);
            } else {
                return (targetFieldNodeClone.name + (value.contains(";") ? ";" + targetFieldNodeClone.desc : "")).equals(value);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
            return mixinMethodNodeClone.name.equals(targetFieldNodeClone.name);
        }
    };

    public AnnotatedMethodTargetFieldTransformerBuilder(Class<? extends MixinClassType<?, AM, ?, ?, TF>> mixinClassType, Class<A> annotation) {
        this.mixinClassType = mixinClassType;
        this.annotation = annotation;
    }

    public AnnotatedMethodTargetFieldTransformerBuilder<A, AM, TF> setTransformer(TransformAnnotatedMethodTargetField<A, AM, TF> transformer) {
        this.transformer = transformer;
        return this;
    }

    public AnnotatedMethodTargetFieldTransformerBuilder<A, AM, TF> setTargetFilter(AnnotatedMethodTargetFieldTargetFilter<A> targetFilter) {
        this.targetFilter = targetFilter;
        return this;
    }

    public AnnotatedMethodTargetFieldBuiltTransformer<A, AM, TF> build() {
        if(transformer == null) throw new IllegalStateException("Can't build before setting a transformer");
        return new AnnotatedMethodTargetFieldBuiltTransformer<>(mixinClassType, annotation, transformer, targetFilter);
    }
}

