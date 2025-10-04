package vi.mixin.api.editors;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public abstract class AnnotatedMethodEditor extends AnnotatedEditor {
    protected final MethodNode mixinMethodNode;

    protected AnnotatedMethodEditor(MethodNode mixinMethodNode, Object targetEditor) {
        super(targetEditor);
        this.mixinMethodNode = mixinMethodNode;
    }

    public final MethodNode getMethodNodeClone() {
        ClassNode clone = new ClassNode();
        mixinMethodNode.accept(clone);
        return clone.methods.getFirst();
    }
}
