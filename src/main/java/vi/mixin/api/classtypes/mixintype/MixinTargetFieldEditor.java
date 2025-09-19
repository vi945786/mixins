package vi.mixin.api.classtypes.mixintype;

import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;

public class MixinTargetFieldEditor extends TargetFieldEditor {
    protected MixinTargetFieldEditor(MixinClassTargetFieldEditor[] targetFieldEditors) {
        super(targetFieldEditors);
    }

    public void makeNonFinal(int index) {
        targetFieldEditors[index].makeNonFinal();
    }

    public void makePublic(int index) {
        targetFieldEditors[index].makePublic();
    }
}
