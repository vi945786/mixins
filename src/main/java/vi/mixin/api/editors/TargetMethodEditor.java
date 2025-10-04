package vi.mixin.api.editors;

import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

public abstract class TargetMethodEditor extends TargetEditor {
    protected final MixinClassTargetMethodEditor targetMethodEditor;

    protected TargetMethodEditor(MixinClassTargetMethodEditor targetMethodEditors, Object annotatedEditor) {
        super(annotatedEditor);
        this.targetMethodEditor = targetMethodEditors;
    }

    public final MethodNode getMethodNodeClone() {
        return targetMethodEditor.getMethodNodeClone();
    }
}
