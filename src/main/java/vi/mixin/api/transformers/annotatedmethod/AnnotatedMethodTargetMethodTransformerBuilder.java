package vi.mixin.api.transformers.annotatedmethod;

import org.objectweb.asm.Type;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.built.AnnotatedMethodTargetMethodBuiltTransformer;
import vi.mixin.api.classtypes.MixinClassType;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

import static vi.mixin.api.transformers.built.AnnotatedMethodTargetMethodBuiltTransformer.*;

public class AnnotatedMethodTargetMethodTransformerBuilder<A extends Annotation, AM extends AnnotatedMethodEditor, TM extends TargetMethodEditor> {
    private final Class<? extends MixinClassType<?, AM, ?, TM, ?>> mixinClassType;
    private final Class<A> annotation;

    private TransformAnnotatedMethodTargetMethod<A, AM, TM> transformer;
    private AnnotatedMethodTargetMethodTargetFilter<A> targetFilter = (mixinMethodNodeClone, targetMethodNodeClone, annotation) -> {
        try {
            String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
            if(value.isEmpty()) {
                return targetMethodNodeClone.name.equals(mixinMethodNodeClone.name) && mixinMethodNodeClone.desc.equals(targetMethodNodeClone.desc);
            } else {
                boolean onlyName = !value.contains("(");
                return (targetMethodNodeClone.name + (onlyName ? "" : targetMethodNodeClone.desc)).equals(value);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
            throw new MixinFormatException(annotation.annotationType().getName(), "use setTargetFilter when building the transformer to set a custom filter function");
        }
    };

    public AnnotatedMethodTargetMethodTransformerBuilder(Class<? extends MixinClassType<?, AM, ?, TM, ?>> mixinClassType, Class<A> annotation) {
        this.mixinClassType = mixinClassType;
        this.annotation = annotation;
    }

    public AnnotatedMethodTargetMethodTransformerBuilder<A, AM, TM> setTransformer(TransformAnnotatedMethodTargetMethod<A, AM, TM> transformer) {
        this.transformer = transformer;
        return this;
    }

    public AnnotatedMethodTargetMethodTransformerBuilder<A, AM, TM> setTargetFilter(AnnotatedMethodTargetMethodTargetFilter<A> targetFilter) {
        this.targetFilter = targetFilter;
        return this;
    }

    public AnnotatedMethodTargetMethodBuiltTransformer<A, AM, TM> build() {
        if(transformer == null) throw new IllegalStateException("Can't build before setting a transformer");
        return new AnnotatedMethodTargetMethodBuiltTransformer<>(mixinClassType, annotation, transformer, targetFilter);
    }
}
