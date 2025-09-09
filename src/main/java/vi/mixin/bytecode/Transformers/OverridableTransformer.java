package vi.mixin.bytecode.Transformers;

import vi.mixin.api.annotations.methods.Overridable;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;

public class OverridableTransformer implements MethodTransformer<Overridable> {

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Overridable mixinAnnotation, ClassEditor targetClassEditor) {
        targetClassEditor.getMethodEditor(mixinAnnotation.value()).makeNonFinal();
    }
}
