package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Overridable;
import vi.mixin.api.classtypes.extendertype.ExtenderAnnotatedMethodEditor;
import vi.mixin.api.classtypes.extendertype.ExtenderMixinClassType;
import vi.mixin.api.classtypes.extendertype.ExtenderTargetMethodEditor;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.transformers.TransformerBuilder;
import vi.mixin.api.transformers.TransformerSupplier;

import java.util.List;

@SuppressWarnings("unused")
public class OverridableTransformer implements TransformerSupplier {

    private static boolean targetFilter(MethodNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, Overridable annotation) {
        if(!targetMethodNodeClone.name.equals(mixinMethodNodeClone.name)) return false;
        if(annotation.value().isEmpty()) return targetMethodNodeClone.desc.split("\\)")[0].equals(mixinMethodNodeClone.desc.split("\\)")[0]);
        if(annotation.value().startsWith("(")) return targetMethodNodeClone.desc.equals(annotation.value());
        return targetMethodNodeClone.desc.split("\\)")[0].equals(annotation.value());
    }

    private static void validate(ExtenderAnnotatedMethodEditor mixinEditor, ExtenderTargetMethodEditor targetEditor, ClassNode mixinClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        String name = "@Overridable " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if(targetMethodNode.name.equals("<init>")) throw new MixinFormatException(name, "overriding a constructor is not allowed. use @New");
        if((mixinMethodNode.access & ACC_STATIC) != 0) throw new MixinFormatException(name, "should be not static");
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.equals(Type.getReturnType(targetMethodNode.desc)) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + Type.getReturnType(targetMethodNode.desc) + ", " + Type.getType(Object.class));
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
                        if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && (!mixinArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE))
                throw new MixinFormatException(name, "valid types for argument number " + (i+1) + " are: " + targetArgumentTypes[i] + (targetArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE ? "" : ", " + Type.getType(Object.class)));
        }
    }

    private static void transform(ExtenderAnnotatedMethodEditor mixinEditor, ExtenderTargetMethodEditor targetEditor, Overridable annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(mixinEditor, targetEditor, mixinClassNodeClone);
        targetEditor.makeNonFinal();
        targetEditor.makePublic();
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.getTransformerBuilder(ExtenderMixinClassType.class).annotation(Overridable.class).annotatedMethod().targetMethod().transformFunction(OverridableTransformer::transform).targetFilter(OverridableTransformer::targetFilter).allowTargetsInSuper().build()
        );
    }
}
