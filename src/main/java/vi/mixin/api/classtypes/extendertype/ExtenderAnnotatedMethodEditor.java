package vi.mixin.api.classtypes.extendertype;

import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.editors.AnnotatedMethodEditor;

public class ExtenderAnnotatedMethodEditor extends AnnotatedMethodEditor {
    MethodInsnNode invoke = null;
    boolean delete = false;

    protected ExtenderAnnotatedMethodEditor(MethodNode mixinMethodNode, Object targetEditors) {
        super(mixinMethodNode, targetEditors);
    }

    public void changeInvoke(MethodInsnNode methodInsnNode) {
        invoke = methodInsnNode;
    }

    public void delete() {
        delete = true;
    }
}
