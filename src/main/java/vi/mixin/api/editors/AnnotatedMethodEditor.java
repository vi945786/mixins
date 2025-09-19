package vi.mixin.api.editors;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public abstract class AnnotatedMethodEditor {
    protected final MethodNode mixinMethodNode;

    protected AnnotatedMethodEditor(MethodNode mixinMethodNode) {
        this.mixinMethodNode = mixinMethodNode;
    }

    public final MethodNode getMethodNodeClone() {
        ClassNode clone = new ClassNode();
        mixinMethodNode.accept(clone);
        return clone.methods.getFirst();
    }
}
