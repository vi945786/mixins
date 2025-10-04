package vi.mixin.api.classtypes.extendertype;

import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

public class ExtenderTargetMethodEditor extends TargetMethodEditor {

    public ExtenderTargetMethodEditor(MixinClassTargetMethodEditor targetMethodEditors, Object annotatedEditor) {
        super(targetMethodEditors, annotatedEditor);
    }

    public void makeNonFinal() {
        targetMethodEditor.makeNonFinal();
    }

    public void makePublic() {
        targetMethodEditor.makePublic();
    }
}
