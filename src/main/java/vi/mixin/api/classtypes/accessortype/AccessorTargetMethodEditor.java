package vi.mixin.api.classtypes.accessortype;

import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

public class AccessorTargetMethodEditor extends TargetMethodEditor {
    protected AccessorTargetMethodEditor(MixinClassTargetMethodEditor targetMethodEditors, Object mixinEditor) {
        super(targetMethodEditors, mixinEditor);
    }

    public void makePublic() {
        targetMethodEditor.makePublic();
    }
}
