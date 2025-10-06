package vi.mixin.api.classtypes.mixintype;

import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.TargetFieldManipulator;

public class MixinTargetFieldEditor extends TargetFieldEditor {
    protected MixinTargetFieldEditor(TargetFieldManipulator targetFieldEditors, Object annotatedEditor) {
        super(targetFieldEditors, annotatedEditor);
    }

    public void makeNonFinal() {
        targetFieldEditor.makeNonFinal();
    }

    public void makePublic() {
        targetFieldEditor.makePublic();
    }
}
