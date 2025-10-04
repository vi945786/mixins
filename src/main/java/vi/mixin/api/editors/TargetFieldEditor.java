package vi.mixin.api.editors;

import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;

public abstract class TargetFieldEditor extends TargetEditor {
    protected final MixinClassTargetFieldEditor targetFieldEditor;

    protected TargetFieldEditor(MixinClassTargetFieldEditor targetFieldEditors, Object annotatedEditor) {
        super(annotatedEditor);
        this.targetFieldEditor = targetFieldEditors;
    }

    public final FieldNode getFieldNodeClone() {
        return targetFieldEditor.getFieldNodeClone();
    }
}
