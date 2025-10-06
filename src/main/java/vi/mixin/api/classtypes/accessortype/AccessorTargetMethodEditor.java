package vi.mixin.api.classtypes.accessortype;

import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.TargetMethodManipulator;

public class AccessorTargetMethodEditor extends TargetMethodEditor {
    protected AccessorTargetMethodEditor(TargetMethodManipulator targetMethodEditors, Object annotatedEditor) {
        super(targetMethodEditors, annotatedEditor);
    }

    public void makePublic() {
        targetMethodEditor.makePublic();
    }
}
