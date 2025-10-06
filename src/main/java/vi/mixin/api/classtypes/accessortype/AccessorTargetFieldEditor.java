package vi.mixin.api.classtypes.accessortype;

import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.TargetFieldManipulator;

public class AccessorTargetFieldEditor extends TargetFieldEditor {
    protected AccessorTargetFieldEditor(TargetFieldManipulator targetFieldEditors, Object annotatedEditor) {
        super(targetFieldEditors, annotatedEditor);
    }

    public void makePublic() {
        targetFieldEditor.makePublic();
    }

    public void makeNonFinal() {
        targetFieldEditor.makeNonFinal();
    }
}
