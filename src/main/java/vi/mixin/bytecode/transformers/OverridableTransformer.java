package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.Overridable;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;

public class OverridableTransformer implements MethodTransformer<Overridable> {

    private static void validate(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Overridable mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Overridable " + mixinClassEditor.getName() + "." + mixinMethodEditor.getName();
        FieldEditor target = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        if(target == null) throw new MixinFormatException(name, "target doesn't exist");
        if((mixinMethodEditor.getAccess() & ACC_STATIC) == 1) throw new MixinFormatException(name, "should be not static");
        Type returnType = Type.getReturnType(mixinMethodEditor.getDesc());
        if(!returnType.equals(Type.getType(target.getDesc())) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + target.getDesc() + ", " + Type.getType(Object.class));
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodEditor.getDesc());
        Type[] targetArgumentTypes = Type.getArgumentTypes(target.getDesc());
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
        if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) == null) throw new MixinFormatException(name, "defining class doesn't have @Extends annotation");
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Overridable mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinMethodEditor, mixinAnnotation, targetClassEditor);
        targetClassEditor.getMethodEditor(mixinAnnotation.value()).makeNonFinal();
    }
}
