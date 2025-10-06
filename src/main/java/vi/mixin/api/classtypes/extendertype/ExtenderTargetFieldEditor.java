package vi.mixin.api.classtypes.extendertype;

import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.TargetFieldManipulator;

public class ExtenderTargetFieldEditor extends TargetFieldEditor {
    protected ExtenderTargetFieldEditor(TargetFieldManipulator targetFieldEditors, Object annotatedEditor) {
        super(targetFieldEditors, annotatedEditor);
    }

    public void makeNonFinal() {
        targetFieldEditor.makeNonFinal();
    }

    public void makePublic() {
        targetFieldEditor.makePublic();
    }
}
