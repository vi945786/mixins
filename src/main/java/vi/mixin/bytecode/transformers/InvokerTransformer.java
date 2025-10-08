package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Invoker;
import vi.mixin.api.classtypes.accessortype.AccessorAnnotatedMethodEditor;
import vi.mixin.api.classtypes.accessortype.AccessorMixinClassType;
import vi.mixin.api.classtypes.accessortype.AccessorTargetMethodEditor;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.transformers.TransformerBuilder;
import vi.mixin.api.util.TransformerHelper;
import vi.mixin.api.transformers.TransformerSupplier;

import java.util.List;

import static vi.mixin.api.util.TransformerHelper.addLoadOpcodesOfMethod;

@SuppressWarnings("unused")
public class InvokerTransformer implements TransformerSupplier {

    private static void validate(AccessorAnnotatedMethodEditor mixinEditor, AccessorTargetMethodEditor targetEditor, ClassNode mixinClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        String name = "@Invoker " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if (targetMethodNode.name.equals("<init>"))
            throw new MixinFormatException(name, "invoking a constructor is not allowed. use @New");
        if ((targetMethodNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC))
            throw new MixinFormatException(name, "should be " + ((targetMethodNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if (!returnType.equals(Type.getReturnType(targetMethodNode.desc)) && !returnType.equals(Type.getType(Object.class)))
            throw new MixinFormatException(name, "valid return types are: " + Type.getReturnType(targetMethodNode.desc) + ", " + Type.getType(Object.class));
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if (mixinArgumentTypes.length != targetArgumentTypes.length)
            throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && (!mixinArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE))
                throw new MixinFormatException(name, "valid types for argument number " + (i + 1) + " are: " + targetArgumentTypes[i] + (targetArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE ? "" : ", " + Type.getType(Object.class)));
        }
    }

    private static void transform(AccessorAnnotatedMethodEditor mixinEditor, AccessorTargetMethodEditor targetEditor, Invoker annotation, ClassNode mixinClassNodeClone, ClassNode targetOriginClassNodeClone) {
        validate(mixinEditor, targetEditor, mixinClassNodeClone);
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        targetEditor.makePublic();

        if(!targetMethodNode.name.equals(mixinMethodNode.name)) {
            boolean isStatic = (targetMethodNode.access & ACC_STATIC) != 0;

            int invokeOpcode = INVOKESPECIAL;
            if(isStatic) invokeOpcode = INVOKESTATIC;
            else if((targetOriginClassNodeClone.access & ACC_INTERFACE) != 0) invokeOpcode = INVOKEINTERFACE;

            int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(mixinMethodNode.desc));

            InsnList insnList = new InsnList();
            addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(mixinMethodNode.desc), isStatic);

            insnList.add(new MethodInsnNode(invokeOpcode, targetOriginClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
            insnList.add(new InsnNode(returnOpcode));
            mixinEditor.setBytecode(insnList);
        }
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.getTransformerBuilder(AccessorMixinClassType.class).annotation(Invoker.class).annotatedMethod().targetMethod().transformFunction(InvokerTransformer::transform).build()
        );
    }
}
