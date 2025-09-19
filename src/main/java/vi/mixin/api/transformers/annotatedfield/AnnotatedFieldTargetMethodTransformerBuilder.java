package vi.mixin.api.transformers.annotatedfield;

import vi.mixin.api.MixinFormatException;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.transformers.built.AnnotatedFieldTargetMethodBuiltTransformer;
import vi.mixin.api.classtypes.MixinClassType;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

import static vi.mixin.api.transformers.built.AnnotatedFieldTargetMethodBuiltTransformer.AnnotatedFieldTargetMethodTargetFilter;
import static vi.mixin.api.transformers.built.AnnotatedFieldTargetMethodBuiltTransformer.TransformAnnotatedFieldTargetMethod;

public class AnnotatedFieldTargetMethodTransformerBuilder<A extends Annotation, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor> {
    private final Class<? extends MixinClassType<?, ?, AF, TM, ?>> mixinClassType;
    private final Class<A> annotation;

    private TransformAnnotatedFieldTargetMethod<A, AF, TM> transformer;
    private AnnotatedFieldTargetMethodTargetFilter<A> targetFilter = (mixinFieldNodeClone, targetMethodNodeClone, annotation) -> {
        try {
            String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
            if(value.isEmpty()) {
                return mixinFieldNodeClone.name.equals(targetMethodNodeClone.name);
            } else {
                return targetMethodNodeClone.name.equals(value);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
            throw new MixinFormatException(annotation.annotationType().getName(), "use setTargetFilter when building the transformer to set a custom filter function");
        }
    };

    public AnnotatedFieldTargetMethodTransformerBuilder(Class<? extends MixinClassType<?, ?, AF, TM, ?>> mixinClassType, Class<A> annotation) {
        this.mixinClassType = mixinClassType;
        this.annotation = annotation;
    }

    public AnnotatedFieldTargetMethodTransformerBuilder<A, AF, TM> setTransformer(TransformAnnotatedFieldTargetMethod<A, AF, TM> transformer) {
        this.transformer = transformer;
        return this;
    }

    public AnnotatedFieldTargetMethodTransformerBuilder<A, AF, TM> setTargetFilter(AnnotatedFieldTargetMethodTargetFilter<A> targetFilter) {
        this.targetFilter = targetFilter;
        return this;
    }

    public AnnotatedFieldTargetMethodBuiltTransformer<A, AF, TM> build() {
        if(transformer == null) throw new IllegalStateException("Can't build before setting a transformer");
        return new AnnotatedFieldTargetMethodBuiltTransformer<>(mixinClassType, annotation, transformer, targetFilter);
    }
}
