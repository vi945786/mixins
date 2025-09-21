package vi.mixin.api.editors;

import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

public abstract class TargetMethodEditor {
    protected final MixinClassTargetMethodEditor targetMethodEditor;
    protected final Object mixinEditor;

    protected TargetMethodEditor(MixinClassTargetMethodEditor targetMethodEditors, Object mixinEditor) {
        this.targetMethodEditor = targetMethodEditors;
        this.mixinEditor = mixinEditor;
    }

    public final MethodNode getMethodNodeClone() {
        return targetMethodEditor.getMethodNodeClone();
    }
}
