package vi.mixin.api.transformers;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.transformers.targeteditors.TargetFieldEditor;

public abstract class FieldEditor {
    protected final MethodNode mixinMethodNode;
    protected final FieldNode mixinFieldNode;
    protected final TargetFieldEditor[] targetFieldEditors;

    protected FieldEditor(MethodNode mixinMethodNode, TargetFieldEditor[] targetFieldEditors) {
        this.mixinMethodNode = mixinMethodNode;
        this.targetFieldEditors = targetFieldEditors;

        mixinFieldNode = null;
    }

    protected FieldEditor(FieldNode mixinFieldNode, TargetFieldEditor[] targetFieldEditors) {
        this.mixinFieldNode = mixinFieldNode;
        this.targetFieldEditors = targetFieldEditors;

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
        return targetFieldEditors.length;
    }

    public final FieldNode getTargetFieldNodeClone(int index) {
        return targetFieldEditors[index].getFieldNodeClone();
    }
}
