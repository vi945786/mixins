package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Getter;
import vi.mixin.api.classtypes.accessortype.AccessorAnnotatedMethodEditor;
import vi.mixin.api.classtypes.accessortype.AccessorMixinClassType;
import vi.mixin.api.classtypes.accessortype.AccessorTargetFieldEditor;
import vi.mixin.api.util.TransformerHelper;
import vi.mixin.api.transformers.TransformerSupplier;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.transformers.TransformerBuilder;

import java.util.List;

@SuppressWarnings("unused")
public class GetterTransformer implements TransformerSupplier {

    private static boolean targetFilter(MethodNode mixinMethodNodeClone, FieldNode targetFieldNodeClone, Getter annotation) {
        if(annotation.value().isEmpty()) {
            if(!mixinMethodNodeClone.name.startsWith("get")) return false;
            return targetFieldNodeClone.name.equals(mixinMethodNodeClone.name.substring(3, 4).toLowerCase() + mixinMethodNodeClone.name.substring(4));
        }
        return targetFieldNodeClone.name.equals(annotation.value());
    }

    private static void validate(AccessorAnnotatedMethodEditor mixinEditor, AccessorTargetFieldEditor targetEditor, ClassNode mixinClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        String name = "@Getter " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if((targetFieldNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((targetFieldNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.equals(Type.getType(targetFieldNode.desc)) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + targetFieldNode.desc + ", " + Type.getType(Object.class));
        if(Type.getArgumentTypes(mixinMethodNode.desc).length != 0) throw new MixinFormatException(name, "takes arguments");
    }

    private static void transform(AccessorAnnotatedMethodEditor mixinEditor, AccessorTargetFieldEditor targetEditor, Getter annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(mixinEditor, targetEditor, mixinClassNodeClone);
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        FieldNode targetFieldNode = targetEditor.getFieldNodeClone();

        boolean isStatic = (targetFieldNode.access & ACC_STATIC) != 0;

        int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(mixinMethodNode.desc));

        targetEditor.makePublic();

        InsnList insnList = new InsnList();
        if (!isStatic) insnList.add(new VarInsnNode(ALOAD, 0));
        insnList.add(new FieldInsnNode(isStatic ? GETSTATIC : GETFIELD, targetClassNodeClone.name, targetFieldNode.name, targetFieldNode.desc));
        insnList.add(new InsnNode(returnOpcode));

        mixinEditor.setBytecode(insnList);
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.getTransformerBuilder(AccessorMixinClassType.class).annotation(Getter.class).annotatedMethod().targetField().transformFunction(GetterTransformer::transform).targetFilter(GetterTransformer::targetFilter).build()
        );
    }
}
