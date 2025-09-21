package vi.mixin.api.classtypes.extendertype;

import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;

public class ExtenderTargetFieldEditor extends TargetFieldEditor {
    protected ExtenderTargetFieldEditor(MixinClassTargetFieldEditor targetFieldEditors, Object mixinEditor) {
        super(targetFieldEditors, mixinEditor);
    }

    public void makeNonFinal() {
        targetFieldEditor.makeNonFinal();
    }

    public void makePublic() {
        targetFieldEditor.makePublic();
    }
}
