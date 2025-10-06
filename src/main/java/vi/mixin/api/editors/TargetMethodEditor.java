package vi.mixin.api.editors;

import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.classtypes.targeteditors.TargetMethodManipulator;

public abstract class TargetMethodEditor extends TargetEditor {
    protected final TargetMethodManipulator targetMethodEditor;

    protected TargetMethodEditor(TargetMethodManipulator targetMethodEditors, Object annotatedEditor) {
        super(annotatedEditor);
        this.targetMethodEditor = targetMethodEditors;
    }

    public final MethodNode getMethodNodeClone() {
        return targetMethodEditor.getMethodNodeClone();
    }
}
