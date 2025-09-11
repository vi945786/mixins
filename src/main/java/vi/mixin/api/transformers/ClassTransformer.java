package vi.mixin.api.transformers;

import vi.mixin.api.editors.ClassEditor;

import java.lang.annotation.Annotation;

public non-sealed interface ClassTransformer<T extends Annotation> extends Transformer {

    void transform(ClassEditor mixinClassEditor, T mixinAnnotation, ClassEditor targetClassEditor);

}

