package vi.mixin.bytecode.Transformers;

import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;

public class NewTransformer implements MethodTransformer<New> {

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, New mixinAnnotation, ClassEditor targetClassEditor) {
        throw new UnsupportedOperationException(); //TODO
    }
}
