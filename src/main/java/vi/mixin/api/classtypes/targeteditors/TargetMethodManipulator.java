package vi.mixin.api.classtypes.targeteditors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class TargetMethodManipulator {
    private final TargetInsnListManipulator.OpcodeStates opcodeStates;

    private final String className;
    private final MethodNode modified;
    private final MethodNode original;

    public TargetMethodManipulator(String className, MethodNode methodNode, MethodNode original, TargetInsnListManipulator.OpcodeStates opcodeStates) {
        this.opcodeStates = opcodeStates;

        this.className = className;
        this.modified = methodNode;
        this.original = original;
    }

    public MethodNode getMethodNodeClone() {
        ClassNode clone = new ClassNode();
        original.accept(clone);
        return clone.methods.getFirst();
    }

    public void makePublic() {
        modified.access |= Opcodes.ACC_PUBLIC;
        modified.access &= ~Opcodes.ACC_PRIVATE;
        modified.access &= ~Opcodes.ACC_PROTECTED;

        original.access |= Opcodes.ACC_PUBLIC;
        original.access &= ~Opcodes.ACC_PRIVATE;
        original.access &= ~Opcodes.ACC_PROTECTED;
    }

    public void makeNonFinal() {
        modified.access &= ~Opcodes.ACC_FINAL;

        original.access &= ~Opcodes.ACC_FINAL;
    }

    public void makeNonAbstract() {
        modified.access &= ~Opcodes.ACC_ABSTRACT;

        original.access &= ~Opcodes.ACC_ABSTRACT;
    }

    public TargetInsnListManipulator getInsnListEditor() {
        return new TargetInsnListManipulator(className + "." + original.name + original.desc, modified.instructions, original.instructions, opcodeStates);
    }
}
