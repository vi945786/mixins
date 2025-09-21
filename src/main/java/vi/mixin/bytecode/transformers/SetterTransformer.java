package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Setter;
import vi.mixin.api.classtypes.accessortype.AccessorAnnotatedMethodEditor;
import vi.mixin.api.classtypes.accessortype.AccessorMixinClassType;
import vi.mixin.api.classtypes.accessortype.AccessorTargetFieldEditor;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.transformers.TransformerBuilder;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.TransformerSupplier;

import java.util.List;

public class SetterTransformer implements TransformerSupplier {

    private static boolean targetFilter(MethodNode mixinMethodNodeClone, FieldNode targetFieldNodeClone, Setter annotation) {
        if(annotation.value().isEmpty()) {
            if(!mixinMethodNodeClone.name.startsWith("set")) return false;
            return targetFieldNodeClone.name.equals(mixinMethodNodeClone.name.substring(3, 4).toLowerCase() + mixinMethodNodeClone.name.substring(4));
        }
        return targetFieldNodeClone.name.equals(annotation.value());
    }

    private static void validate(AccessorAnnotatedMethodEditor mixinEditor, AccessorTargetFieldEditor targetEditor, Setter annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        String name = "@Setter " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if((targetFieldNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((targetFieldNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        if(!Type.getReturnType(mixinMethodNode.desc).equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "should return void");
        Type[] argumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        if(argumentTypes.length != 1 && !argumentTypes[0].equals(Type.getType(targetFieldNode.desc)) && !argumentTypes[0].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid arguments are: " + targetFieldNode.desc + ", " + Type.getType(Object.class));
    }

    private static void transform(AccessorAnnotatedMethodEditor mixinEditor, AccessorTargetFieldEditor targetEditor, Setter annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        targetEditor.makePublic();
        targetEditor.makeNonFinal();

        boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;

        InsnList insnList = new InsnList();
        TransformerHelper.addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(mixinMethodNode.desc), isStatic);
        insnList.add(new FieldInsnNode(isStatic ? PUTSTATIC : PUTFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
        insnList.add(new InsnNode(RETURN));

        mixinEditor.setBytecode(insnList);
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.annotatedMethodTransformerBuilder(AccessorMixinClassType.class, Setter.class).withFieldTarget().setTargetFilter(SetterTransformer::targetFilter).setTransformer(SetterTransformer::transform).build()
        );
    }
}
