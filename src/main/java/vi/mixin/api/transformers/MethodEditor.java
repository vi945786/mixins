package vi.mixin.api.transformers;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.transformers.targeteditors.TargetMethodEditor;

public abstract class MethodEditor {
    protected final MethodNode mixinMethodNode;
    protected final FieldNode mixinFieldNode;
    protected final TargetMethodEditor[] targetMethodEditors;

    protected MethodEditor(MethodNode mixinMethodNode, TargetMethodEditor[] targetMethodEditors) {
        this.mixinMethodNode = mixinMethodNode;
        this.targetMethodEditors = targetMethodEditors;

        mixinFieldNode = null;
    }

    protected MethodEditor(FieldNode mixinFieldNode, TargetMethodEditor[] targetMethodEditors) {
        this.mixinFieldNode = mixinFieldNode;
        this.targetMethodEditors = targetMethodEditors;

        mixinMethodNode = null;
    }

    public final MethodNode getMixinMethodNodeClone() {
        ClassNode clone = new ClassNode();
        mixinMethodNode.accept(clone);
        return clone.methods.getFirst();
    }

    public final FieldNode getMixinFieldNodeClone() {
        ClassNode clone = new ClassNode();
        mixinFieldNode.accept(clone);
        return clone.fields.getFirst();
    }

    public final int getNumberOfTargets() {
        return targetMethodEditors.length;
    }

    public final MethodNode getTargetMethodNodeClone(int index) {
        return targetMethodEditors[index].getMethodNodeClone();
    }
}
