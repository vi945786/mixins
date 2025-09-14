package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.fields.Mutable;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.transformers.FieldTransformer;

public class MutableTransformer implements FieldTransformer<Mutable> {

    private static void validate(ClassEditor mixinClassEditor, FieldEditor mixinFieldEditor, Mutable mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Mutable " + mixinClassEditor.getName() + "." + mixinFieldEditor.getName();
        FieldEditor target = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        if(target == null) throw new MixinFormatException(name, "target doesn't exist");
        if((target.getAccess() & ACC_STATIC) == (mixinFieldEditor.getAccess() & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((target.getAccess() & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type type = Type.getType(mixinFieldEditor.getDesc());
        if(!type.equals(Type.getType(target.getDesc())) && !type.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types are: " + target.getDesc() + ", " + Type.getType(Object.class));
        if((mixinClassEditor.getAccess() & ACC_INTERFACE) != 0) throw new MixinFormatException(name, "defining class is an interface");
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, FieldEditor mixinFieldEditor, Mutable mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinFieldEditor, mixinAnnotation, targetClassEditor);
        FieldEditor targetFieldEditor = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        targetFieldEditor.makeNonFinal();
    }
}
