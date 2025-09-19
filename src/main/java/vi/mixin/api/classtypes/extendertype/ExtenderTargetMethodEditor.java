package vi.mixin.api.classtypes.extendertype;

import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

public class ExtenderTargetMethodEditor extends TargetMethodEditor {

    public ExtenderTargetMethodEditor(MixinClassTargetMethodEditor[] targetMethodEditors) {
        super(targetMethodEditors);
    }

    public void makeNonFinal(int index) {
        targetMethodEditors[index].makeNonFinal();
    }

    public void makePublic(int index) {
        targetMethodEditors[index].makePublic();
    }
}
