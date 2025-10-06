package vi.mixin.api.editors;

import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.classtypes.targeteditors.TargetFieldManipulator;

public abstract class TargetFieldEditor extends TargetEditor {
    protected final TargetFieldManipulator targetFieldEditor;

    protected TargetFieldEditor(TargetFieldManipulator targetFieldEditors, Object annotatedEditor) {
        super(annotatedEditor);
        this.targetFieldEditor = targetFieldEditors;
    }

    public final FieldNode getFieldNodeClone() {
        return targetFieldEditor.getFieldNodeClone();
    }
}
