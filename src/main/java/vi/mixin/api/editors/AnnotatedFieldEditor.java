package vi.mixin.api.editors;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public abstract class AnnotatedFieldEditor extends AnnotatedEditor {
    protected final FieldNode mixinFieldNode;

    protected AnnotatedFieldEditor(FieldNode mixinFieldNode, Object targetEditor) {
        super(targetEditor);
        this.mixinFieldNode = mixinFieldNode;
    }

    public final FieldNode getFieldNodeClone() {
        ClassNode clone = new ClassNode();
        mixinFieldNode.accept(clone);
        return clone.fields.getFirst();
    }
}
