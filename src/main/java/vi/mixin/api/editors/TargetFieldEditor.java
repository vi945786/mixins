package vi.mixin.api.editors;

import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;

public abstract class TargetFieldEditor {
    protected final MixinClassTargetFieldEditor[] targetFieldEditors;

    protected TargetFieldEditor(MixinClassTargetFieldEditor[] targetFieldEditors) {
        this.targetFieldEditors = targetFieldEditors;
    }

    public final int getNumberOfTargets() {
        return targetFieldEditors.length;
    }

    public final FieldNode getFieldNodeClone(int index) {
        return targetFieldEditors[index].getFieldNodeClone();
    }
}
