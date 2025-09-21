package vi.mixin.api.editors;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public abstract class AnnotatedMethodEditor {
    protected final MethodNode mixinMethodNode;
    protected final Object targetEditors;

    protected AnnotatedMethodEditor(MethodNode mixinMethodNode, Object targetEditors) {
        this.mixinMethodNode = mixinMethodNode;
        this.targetEditors = targetEditors;
    }

    public final MethodNode getMethodNodeClone() {
        ClassNode clone = new ClassNode();
        mixinMethodNode.accept(clone);
        return clone.methods.getFirst();
    }
}
