package vi.mixin.api.classtypes.mixintype;

import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;

public class MixinTargetFieldEditor extends TargetFieldEditor {
    protected MixinTargetFieldEditor(MixinClassTargetFieldEditor targetFieldEditors, Object mixinEditor) {
        super(targetFieldEditors, mixinEditor);
    }

    public void makeNonFinal() {
        targetFieldEditor.makeNonFinal();
    }

    public void makePublic() {
        targetFieldEditor.makePublic();
    }
}
