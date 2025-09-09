package vi.mixin.api.transformers;

import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;

import java.lang.annotation.Annotation;

public non-sealed interface MethodTransformer<T extends Annotation> extends MixinTransformer {

    void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, T mixinAnnotation, ClassEditor targetClassEditor);

}
