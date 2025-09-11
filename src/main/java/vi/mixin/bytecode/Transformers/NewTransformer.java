package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.editors.BytecodeEditor;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;

public class NewTransformer implements MethodTransformer<New> {

    private static void validate(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, New mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@New " + mixinClassEditor.getName() + "." + mixinMethodEditor.getName() + mixinMethodEditor.getDesc();
        MethodEditor target = targetClassEditor.getMethodEditor("<init>(" + mixinAnnotation.value() + ")V");
        if(target == null) throw new MixinFormatException(name, "target doesn't exist");
        if((mixinMethodEditor.getAccess() & ACC_STATIC) == 0) throw new MixinFormatException(name, "should be static");
        Type returnType = Type.getReturnType(mixinMethodEditor.getDesc());
        if(!returnType.getInternalName().equals(targetClassEditor.getName()) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + "L" + targetClassEditor.getName() + ";" + ", " + Type.getType(Object.class));
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodEditor.getDesc());
        Type[] targetArgumentTypes = Type.getArgumentTypes(target.getDesc());
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, New mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinMethodEditor, mixinAnnotation, targetClassEditor);
        MethodEditor targetMethodEditor = targetClassEditor.getMethodEditor("<init>(" + mixinAnnotation.value() + ")V");

        targetMethodEditor.makePublic();
        mixinMethodEditor.makeNonAbstract();

        BytecodeEditor bytecodeEditor = mixinMethodEditor.getBytecodeEditor();
        for (int i = 0; i < bytecodeEditor.getBytecode().size(); i++) {
            bytecodeEditor.remove(i);
        }

        bytecodeEditor.add(0, new TypeInsnNode(NEW, "L" + targetClassEditor.getName() + ";"));
        bytecodeEditor.add(0, new InsnNode(DUP));

        Type[] arguments = Type.getArgumentTypes(targetMethodEditor.getDesc());
        for (int i = 0; i < arguments.length; i++) {
            Type argument = arguments[i];
            int argumentOpcode = switch (argument.getSort()) {
                case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> ILOAD;
                case Type.FLOAT -> FLOAD;
                case Type.LONG -> LLOAD;
                case Type.DOUBLE -> DLOAD;
                default -> ALOAD;
            };
            bytecodeEditor.add(0, new VarInsnNode(argumentOpcode, i));
        }

        bytecodeEditor.add(0, new MethodInsnNode(INVOKESPECIAL, targetClassEditor.getName(), targetMethodEditor.getName(), targetMethodEditor.getDesc()));
        bytecodeEditor.add(0,new InsnNode(ARETURN));

    }
}
