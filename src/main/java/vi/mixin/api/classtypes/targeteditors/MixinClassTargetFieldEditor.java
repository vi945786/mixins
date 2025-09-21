package vi.mixin.api.classtypes.targeteditors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class MixinClassTargetFieldEditor {
    private final FieldNode modified;
    private final FieldNode original;

    public MixinClassTargetFieldEditor(FieldNode fieldNode, FieldNode original) {
        this.modified = fieldNode;
        this.original = original;
    }

    public FieldNode getFieldNodeClone() {
        ClassNode clone = new ClassNode();
        original.accept(clone);
        return clone.fields.getFirst();
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

    public void makeNonSynthetic() {
        modified.access &= ~Opcodes.ACC_SYNTHETIC;

        original.access &= ~Opcodes.ACC_SYNTHETIC;
    }
}
