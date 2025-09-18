package vi.mixin.api.transformers.mixintype;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import vi.mixin.api.transformers.MethodEditor;
import vi.mixin.api.transformers.targeteditors.TargetInsnListEditor;
import vi.mixin.api.transformers.targeteditors.TargetMethodEditor;

public class MixinMethodEditor extends MethodEditor {
    MethodInsnNode invoke = null;
    boolean copy = true;
    boolean delete = false;

    MixinMethodEditor(MethodNode mixinMethodNode, TargetMethodEditor[] targetMethodEditors) {
        super(mixinMethodNode, targetMethodEditors);
    }

    MixinMethodEditor(FieldNode mixinFieldNode, TargetMethodEditor[] targetMethodEditors) {
        super(mixinFieldNode, targetMethodEditors);
    }

    public void changeInvoke(MethodInsnNode methodInsnNode) {
        if(mixinMethodNode == null) throw new UnsupportedOperationException("MixinMethodEditor.changeInvoke is only for method editors with a method annotation");
        invoke = methodInsnNode;
    }

    public void doNotCopyToTarget() {
        if(mixinMethodNode == null) throw new UnsupportedOperationException("MixinMethodEditor.doNotCopyToTarget is only for method editors with a method annotation");
        copy = false;
    }

    public void delete() {
        if(mixinMethodNode == null) throw new UnsupportedOperationException("MixinMethodEditor.delete is only for method editors with a method annotation");
        delete = true;
    }

    public void makeTargetNonFinal(int index) {
        targetMethodEditors[index].makeNonFinal();
    }

    public void makeTargetNonAbstract(int index) {
        targetMethodEditors[index].makeNonAbstract();
    }

    public void makeTargetPublic(int index) {
        targetMethodEditors[index].makePublic();
    }

    public String getUpdatedDesc(String targetClassNodeName) {
        if(copy && !delete && (mixinMethodNode.access & Opcodes.ACC_STATIC) == 0) return MixinClassTransformer.getNewDesc(mixinMethodNode.desc, targetClassNodeName);
        return mixinMethodNode.desc;
    }

    public void setMixinBytecode(InsnList insnList) {
        mixinMethodNode.instructions = insnList;
        mixinMethodNode.access &= ~Opcodes.ACC_ABSTRACT;
    }

    public TargetInsnListEditor getTargetInsnListEditor(int index) {
        return targetMethodEditors[index].getInsnListEditor();
    }
}
