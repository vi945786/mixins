package vi.mixin.api.transformers.accessortype;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.transformers.FieldEditor;
import vi.mixin.api.transformers.targeteditors.TargetFieldEditor;

public class AccessorFieldEditor extends FieldEditor {
    AccessorFieldEditor(MethodNode mixinMethodNode, TargetFieldEditor[] targetFieldEditors) {
        super(mixinMethodNode, targetFieldEditors);
    }

    AccessorFieldEditor(FieldNode mixinFieldNode, TargetFieldEditor[] targetFieldEditors) {
        super(mixinFieldNode, targetFieldEditors);
    }

    public void makeTargetPublic(int index) {
        targetFieldEditors[index].makePublic();
    }

    public void makeTargetNonFinal(int index) {
        targetFieldEditors[index].makeNonFinal();
    }

    public void setMixinBytecode(InsnList insnList) {
        mixinMethodNode.instructions = insnList;
        mixinMethodNode.access &= ~Opcodes.ACC_ABSTRACT;
    }
}