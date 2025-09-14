package vi.mixin.bytecode.Transformers;

import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Transformer;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.transformers.ClassTransformer;

public class ExtendsTransformer implements ClassTransformer<Extends> {

    private static void validate(ClassEditor mixinClassEditor, Extends mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Extends " + mixinClassEditor.getName();
        if(targetClassEditor.getSuperName() != null && !targetClassEditor.getSuperName().equals("java/lang/Object")) throw new MixinFormatException(name, "extends " + targetClassEditor.getSuperName());
        if((mixinClassEditor.getAccess() & ACC_INTERFACE) != 0) throw new MixinFormatException(name, "@Extends is not allowed on interfaces");
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, Extends mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinAnnotation, targetClassEditor);
        targetClassEditor.makePublic();
        targetClassEditor.makeNonFinalOrSealed();
        mixinClassEditor.setSuperName(targetClassEditor.getName());
    }
}
