package vi.mixin.api.transformers.targeteditors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class TargetMethodEditor {
    private final MethodNode modified;
    private final MethodNode original;

    public TargetMethodEditor(MethodNode methodNode, MethodNode original) {
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

    public TargetInsnListEditor getInsnListEditor() {
        return new TargetInsnListEditor(modified.instructions, original.instructions);
    }
}
