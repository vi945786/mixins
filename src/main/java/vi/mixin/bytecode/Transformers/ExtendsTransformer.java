package vi.mixin.bytecode.Transformers;

import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.transformers.ClassTransformer;

public class ExtendsTransformer implements ClassTransformer<Extends> {

    @Override
    public void transform(ClassEditor mixinClassEditor, Extends mixinAnnotation, ClassEditor targetClassEditor) {
        targetClassEditor.makeNonFinalOrSealed();
        mixinClassEditor.setSuperName(targetClassEditor.getName());
    }
}
