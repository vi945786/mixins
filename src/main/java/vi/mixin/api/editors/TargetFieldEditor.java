package vi.mixin.api.editors;

import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;

public abstract class TargetFieldEditor {
    protected final MixinClassTargetFieldEditor targetFieldEditor;
    protected final Object mixinEditor;

    protected TargetFieldEditor(MixinClassTargetFieldEditor targetFieldEditors, Object mixinEditor) {
        this.targetFieldEditor = targetFieldEditors;
        this.mixinEditor = mixinEditor;
    }

    public final FieldNode getFieldNodeClone() {
        return targetFieldEditor.getFieldNodeClone();
    }
}
