package vi.mixin.api.editors;

import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

public abstract class TargetMethodEditor {
    protected final MixinClassTargetMethodEditor[] targetMethodEditors;

    protected TargetMethodEditor(MixinClassTargetMethodEditor[] targetMethodEditors) {
        this.targetMethodEditors = targetMethodEditors;
    }

    public final int getNumberOfTargets() {
        return targetMethodEditors.length;
    }

    public final MethodNode getMethodNodeClone(int index) {
        return targetMethodEditors[index].getMethodNodeClone();
    }
}
