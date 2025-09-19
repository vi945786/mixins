package vi.mixin.api.classtypes.mixintype;

import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetInsnListEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

public class MixinTargetMethodEditor extends TargetMethodEditor {

    public MixinTargetMethodEditor(MixinClassTargetMethodEditor[] targetMethodEditors) {
        super(targetMethodEditors);
    }

    public void makeNonFinal(int index) {
        targetMethodEditors[index].makeNonFinal();
    }

    public void makeNonAbstract(int index) {
        targetMethodEditors[index].makeNonAbstract();
    }

    public void makePublic(int index) {
        targetMethodEditors[index].makePublic();
    }

    public MixinClassTargetInsnListEditor getInsnListEditor(int index) {
        return targetMethodEditors[index].getInsnListEditor();
    }
}
