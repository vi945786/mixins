package vi.mixin.api.transformers;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class TransformerHelper implements Opcodes {

    public static int getReturnOpcode(Type returnType) {
        return switch (returnType.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> IRETURN;
            case Type.FLOAT -> FRETURN;
            case Type.LONG -> LRETURN;
            case Type.DOUBLE -> DRETURN;
            default -> ARETURN;
        };
    }

    public static int getLoadOpcode(Type argument) {
        return switch (argument.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> ILOAD;
            case Type.FLOAT -> FLOAD;
            case Type.LONG -> LLOAD;
            case Type.DOUBLE -> DLOAD;
            default -> ALOAD;
        };
    }

    public static int[] getLoadOpcodes(Type[] arguments) {
        return Arrays.stream(arguments).mapToInt(TransformerHelper::getLoadOpcode).toArray();
    }

    public static void addLoadOpcodesOfMethod(List<AbstractInsnNode> insnNodes, Type[] arguments, boolean isStatic) {
        int[] loadOpcodes = TransformerHelper.getLoadOpcodes(arguments);

        if(!isStatic) insnNodes.add(new VarInsnNode(ALOAD, 0));
        for (int i = 0; i < loadOpcodes.length; i++) {
            insnNodes.add(new VarInsnNode(loadOpcodes[i], i + (isStatic ? 0 : 1)));
        }
    }

    public static MethodEditor getMethodEditor(ClassEditor classEditor, MethodInsnNode methodInsnNode) {
        return classEditor.getAllClassesInHierarchy().stream()
                .filter(editor -> editor.getName().equals(methodInsnNode.owner))
                .map(editor -> editor.getMethodEditor(methodInsnNode.name + methodInsnNode.desc))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }
}
