package vi.mixin.bytecode.Transformers;

import vi.mixin.api.annotations.fields.Mutable;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.transformers.FieldTransformer;

public class MutableTransformer implements FieldTransformer<Mutable> {

    @Override
    public void transform(ClassEditor mixinClassEditor, FieldEditor mixinFieldEditor, Mutable mixinAnnotation, ClassEditor targetClassEditor) {
        FieldEditor targetFieldEditor = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        targetFieldEditor.makeNonFinal();
    }
}
