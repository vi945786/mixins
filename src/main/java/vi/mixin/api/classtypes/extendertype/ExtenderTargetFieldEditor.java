package vi.mixin.api.classtypes.extendertype;

import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;

public class ExtenderTargetFieldEditor extends TargetFieldEditor {
    protected ExtenderTargetFieldEditor(MixinClassTargetFieldEditor[] targetFieldEditors) {
        super(targetFieldEditors);
    }

    public void makeNonFinal(int index) {
        targetFieldEditors[index].makeNonFinal();
    }

    public void makePublic(int index) {
        targetFieldEditors[index].makePublic();
    }
}
