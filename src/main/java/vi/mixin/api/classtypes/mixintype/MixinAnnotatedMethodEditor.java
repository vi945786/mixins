package vi.mixin.api.classtypes.mixintype;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.editors.AnnotatedMethodEditor;

public class MixinAnnotatedMethodEditor extends AnnotatedMethodEditor {
    private final String realTargetClassName;
    private final String replaceName;

    MethodInsnNode invoke = null;
    boolean copy = true;
    boolean delete = false;

    protected MixinAnnotatedMethodEditor(MethodNode mixinMethodNode, Object targetEditor, String realTargetClassName, String replaceName) {
        super(mixinMethodNode, targetEditor);
        this.realTargetClassName = realTargetClassName;
        this.replaceName = replaceName;
    }

    public String getRealTargetClassName() {
        return realTargetClassName;
    }

    @SuppressWarnings("unused")
    public String getReplaceName() {
        return replaceName;
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

    public void makePublic() {
        mixinMethodNode.access |= Opcodes.ACC_PUBLIC;
        mixinMethodNode.access &= ~Opcodes.ACC_PRIVATE;
        mixinMethodNode.access &= ~Opcodes.ACC_PROTECTED;
    }

    public String getUpdatedDesc() {
        if(!delete && (mixinMethodNode.access & Opcodes.ACC_STATIC) == 0) return MixinMixinClassType.getNewDesc(mixinMethodNode.desc, realTargetClassName);
        return mixinMethodNode.desc;
    }

    public void setBytecode(InsnList insnList) {
        mixinMethodNode.instructions = insnList;
        mixinMethodNode.access &= ~Opcodes.ACC_ABSTRACT;
    }
}
