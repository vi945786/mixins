package vi.mixin.api.editors;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public abstract class AnnotatedFieldEditor {
    protected final FieldNode mixinFieldNode;
    protected final Object targetEditors;

    protected AnnotatedFieldEditor(FieldNode mixinFieldNode, Object targetEditors) {
        this.mixinFieldNode = mixinFieldNode;
        this.targetEditors = targetEditors;
    }

    public final FieldNode getFieldNodeClone() {
        ClassNode clone = new ClassNode();
        mixinFieldNode.accept(clone);
        return clone.fields.getFirst();
    }
}
