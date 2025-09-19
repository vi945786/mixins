package vi.mixin.api.classtypes.mixintype;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.editors.AnnotatedMethodEditor;

public class MixinAnnotatedMethodEditor extends AnnotatedMethodEditor {
    MethodInsnNode invoke = null;
    boolean copy = true;
    boolean delete = false;

    protected MixinAnnotatedMethodEditor(MethodNode mixinMethodNode) {
        super(mixinMethodNode);
    }

    public void changeInvoke(MethodInsnNode methodInsnNode) {
        invoke = methodInsnNode;
    }

    public void doNotCopyToTargetClass() {
        copy = false;
    }

    public void delete() {
        delete = true;
    }

    public String getUpdatedDesc(String targetClassNodeName) {
        if(copy && !delete && (mixinMethodNode.access & Opcodes.ACC_STATIC) == 0) return MixinMixinClassType.getNewDesc(mixinMethodNode.desc, targetClassNodeName);
        return mixinMethodNode.desc;
    }

    public void setBytecode(InsnList insnList) {
        mixinMethodNode.instructions = insnList;
        mixinMethodNode.access &= ~Opcodes.ACC_ABSTRACT;
    }
}
