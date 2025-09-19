package vi.mixin.api.classtypes.accessortype;

import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;

public class AccessorTargetFieldEditor extends TargetFieldEditor {
    protected AccessorTargetFieldEditor(MixinClassTargetFieldEditor[] targetFieldEditors) {
        super(targetFieldEditors);
    }

    public void makePublic(int index) {
        targetFieldEditors[index].makePublic();
    }

    public void makeNonFinal(int index) {
        targetFieldEditors[index].makeNonFinal();
    }
}
