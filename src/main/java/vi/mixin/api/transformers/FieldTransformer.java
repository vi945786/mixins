package vi.mixin.api.transformers;

import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;

import java.lang.annotation.Annotation;

public non-sealed interface FieldTransformer<T extends Annotation> extends MixinTransformer {

    void transform(ClassEditor mixinClassEditor, FieldEditor mixinFieldEditor, T mixinAnnotation, ClassEditor targetClassEditor);

}
