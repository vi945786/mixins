package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.Inject;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.injection.Returner;
import vi.mixin.api.injection.ValueReturner;
import vi.mixin.api.transformers.MethodTransformer;
import vi.mixin.api.transformers.TransformerHelper;

import java.util.ArrayList;
import java.util.List;

public class InjectTransformer implements MethodTransformer<Inject> {

    private static void validate(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Inject mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Invoker " + mixinClassEditor.getName() + "." + mixinMethodEditor.getName() + mixinMethodEditor.getDesc();
        MethodEditor target = targetClassEditor.getMethodEditor(mixinAnnotation.method());
        if(target == null) throw new MixinFormatException(name, "target method doesn't exist");
        if((target.getAccess() & ACC_STATIC) != (mixinMethodEditor.getAccess() & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((target.getAccess() & ACC_STATIC) != 0 ? "" : "not") + " static");
        if(!Type.getReturnType(mixinMethodEditor.getDesc()).equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "should return void");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodEditor.getDesc());
        Type[] targetArgumentTypes = Type.getArgumentTypes(target.getDesc());
        if(mixinArgumentTypes.length != targetArgumentTypes.length+1) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length+1 + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + (i+1) + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
        Type returnerType;
        if(Type.getReturnType(target.getDesc()).equals(Type.VOID_TYPE)) returnerType = Type.getType(Returner.class);
        else returnerType = Type.getType(ValueReturner.class);
        if (!mixinArgumentTypes[targetArgumentTypes.length].equals(returnerType)) throw new MixinFormatException(name, "valid types for argument number " + (targetArgumentTypes.length+1) + " are: " + returnerType);

        if((mixinClassEditor.getAccess() & ACC_INTERFACE) != 0) throw new MixinFormatException(name, "defining class is an interface");
        if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) != null) throw new MixinFormatException(name, "defining class has @Extends annotation");
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Inject mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinMethodEditor, mixinAnnotation, targetClassEditor);
        MethodEditor targetMethodEditor = targetClassEditor.getMethodEditor(mixinAnnotation.method());
        List<Integer> indexes = targetMethodEditor.getBytecodeEditor().getAtTargetIndexes(mixinAnnotation.at());

        Type returnType = Type.getReturnType(targetMethodEditor.getDesc());
        boolean isStatic = (targetMethodEditor.getAccess() & ACC_STATIC) != 0;
        String returner = returnType.getSort() == 0 ? "vi/mixin/api/injection/Returner" : "vi/mixin/api/injection/ValueReturner";
        mixinMethodEditor.makeStatic().makePublic();
        String desc = TransformerHelper.makeMethodFakeInstance(mixinMethodEditor, targetClassEditor.getName());

        for(int index : indexes) {
            List<AbstractInsnNode> insnNodes = new ArrayList<>();

            insnNodes.add(new TypeInsnNode(NEW, returner));
            insnNodes.add(new InsnNode(DUP));
            insnNodes.add(new MethodInsnNode(INVOKESPECIAL, returner, "<init>", "()V"));
            insnNodes.add(new InsnNode(DUP));

            if(!isStatic) {
                insnNodes.add(new VarInsnNode(ALOAD, 0));
                insnNodes.add(new InsnNode(SWAP));
            }
            int[] loadOpcodes = TransformerHelper.getLoadOpcodes(Type.getArgumentTypes(targetMethodEditor.getDesc()));

            for (int i = 0; i < loadOpcodes.length; i++) {
                insnNodes.add(new VarInsnNode(loadOpcodes[i], i + (isStatic ? 0 : 1)));
                insnNodes.add(new InsnNode(SWAP));
            }

            insnNodes.add(new MethodInsnNode(INVOKESTATIC, mixinClassEditor.getName(), mixinMethodEditor.getName(), desc));

            LabelNode skipReturn = new LabelNode();
            if(returnType.getSort() != 0) insnNodes.add(new InsnNode(DUP));
            insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL, returner, "isReturned", "()Z"));
            insnNodes.add(new JumpInsnNode(IFEQ, skipReturn));
            if(returnType.getSort() != 0) {

                String methodName = "getReturnValue";
                String methodDesc = "()java/lang/Object";
                if (returnType.getSort() <= 8) {
                    methodName += returnType.getInternalName();
                    methodDesc = "()" + returnType.getInternalName();
                }
                insnNodes.add(new MethodInsnNode(INVOKEVIRTUAL, returner, methodName, methodDesc));
            }

            int returnOpcode = TransformerHelper.getReturnOpcode(returnType);
            insnNodes.add(new InsnNode(returnOpcode));
            insnNodes.add(skipReturn);
            if(returnType.getSort() != 0) insnNodes.add(new InsnNode(POP));

            targetMethodEditor.getBytecodeEditor().add(index, insnNodes);
        }
    }
}
