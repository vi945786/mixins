package vi.mixin.api.transformers.annotatedfield;

import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.transformers.built.AnnotatedFieldTargetFieldBuiltTransformer;
import vi.mixin.api.classtypes.MixinClassType;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

import static vi.mixin.api.transformers.built.AnnotatedFieldTargetFieldBuiltTransformer.AnnotatedFieldTargetFieldTargetFilter;
import static vi.mixin.api.transformers.built.AnnotatedFieldTargetFieldBuiltTransformer.TransformAnnotatedFieldTargetField;

public class AnnotatedFieldTargetFieldTransformerBuilder<A extends Annotation, AF extends AnnotatedFieldEditor, TF extends TargetFieldEditor> {
    private final Class<? extends MixinClassType<?, ?, AF, ?, TF>> mixinClassType;
    private final Class<A> annotation;

    private TransformAnnotatedFieldTargetField<A, AF, TF> transformer;
    private AnnotatedFieldTargetFieldTargetFilter<A> targetFilter = (mixinFieldNodeClone, targetFieldNodeClone, annotation) -> {
        try {
            String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
            if(value.isEmpty()) {
                return mixinFieldNodeClone.name.equals(targetFieldNodeClone.name);
            } else {
                return (targetFieldNodeClone.name + (value.contains(";") ? ";" + targetFieldNodeClone.desc : "")).equals(value);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
            return mixinFieldNodeClone.name.equals(targetFieldNodeClone.name);
        }
    };

    public AnnotatedFieldTargetFieldTransformerBuilder(Class<? extends MixinClassType<?, ?, AF, ?, TF>> mixinClassType, Class<A> annotation) {
        this.mixinClassType = mixinClassType;
        this.annotation = annotation;
    }

    public AnnotatedFieldTargetFieldTransformerBuilder<A, AF, TF> setTransformer(TransformAnnotatedFieldTargetField<A, AF, TF> transformer) {
        this.transformer = transformer;
        return this;
    }

    public AnnotatedFieldTargetFieldTransformerBuilder<A, AF, TF> setTargetFilter(AnnotatedFieldTargetFieldTargetFilter<A> targetFilter) {
        this.targetFilter = targetFilter;
        return this;
    }

    public AnnotatedFieldTargetFieldBuiltTransformer<?, AF, TF> build() {
        if(transformer == null) throw new IllegalStateException("Can't build before setting a transformer");
        return new AnnotatedFieldTargetFieldBuiltTransformer<>(mixinClassType, annotation, transformer, targetFilter);
    }
}

