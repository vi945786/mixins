package vi.mixin.api.editors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;

public class FieldEditor {
    final FieldNode fieldNode;
    private final int access;

    public FieldEditor(FieldNode fieldNode) {
        this.fieldNode = fieldNode;
        access = fieldNode.access;
    }

    public FieldEditor makePublic() {
        fieldNode.access |= Opcodes.ACC_PUBLIC;
        fieldNode.access &= ~Opcodes.ACC_PRIVATE;
        fieldNode.access &= ~Opcodes.ACC_PROTECTED;
        return this;
    }

    public FieldEditor makeNonFinal() {
        fieldNode.access &= ~Opcodes.ACC_FINAL;
        return this;
    }

    public String getName() {
        return fieldNode.name;
    }

    public String getDesc() {
        return fieldNode.desc;
    }

    public int getAccess() {
        return access;
    }

    /**
     * if the default value isn't primitive, Class or String it is not a default value.
     * it is set in the <init> or <clinit> method
     */
    public Object getDefaultValue() {
        return fieldNode.value;
    }

    /**
     * only applies to generic types
     */
    public Object getSignature() {
        return fieldNode.signature;
    }
}
