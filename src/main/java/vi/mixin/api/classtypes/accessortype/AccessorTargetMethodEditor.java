package vi.mixin.api.classtypes.accessortype;

import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

public class AccessorTargetMethodEditor extends TargetMethodEditor {
    protected AccessorTargetMethodEditor(MixinClassTargetMethodEditor[] targetMethodEditors) {
        super(targetMethodEditors);
    }

    public void makePublic(int index) {
        targetMethodEditors[index].makePublic();
    }
}
