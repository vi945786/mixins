package vi.mixin.api.transformers.extendertype;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.transformers.MethodEditor;
import vi.mixin.api.transformers.mixintype.MixinClassTransformer;
import vi.mixin.api.transformers.targeteditors.TargetInsnListEditor;
import vi.mixin.api.transformers.targeteditors.TargetMethodEditor;

public class ExtenderMethodEditor extends MethodEditor {
    MethodInsnNode invoke = null;
    boolean delete = false;

    ExtenderMethodEditor(MethodNode mixinMethodNode, TargetMethodEditor[] targetMethodEditors) {
        super(mixinMethodNode, targetMethodEditors);
    }

    ExtenderMethodEditor(FieldNode mixinFieldNode, TargetMethodEditor[] targetMethodEditors) {
        super(mixinFieldNode, targetMethodEditors);
    }

    public void changeInvoke(MethodInsnNode methodInsnNode) {
        if(mixinMethodNode == null) throw new UnsupportedOperationException("ExtenderMethodEditor.changeInvoke is only for method editors with a method annotation");
        invoke = methodInsnNode;
    }

    public void delete() {
        if(mixinMethodNode == null) throw new UnsupportedOperationException("ExtenderMethodEditor.delete is only for method editors with a method annotation");
        delete = true;
    }

    public void makeTargetNonFinal(int index) {
        targetMethodEditors[index].makeNonFinal();
    }

    public void makeTargetPublic(int index) {
        targetMethodEditors[index].makePublic();
    }


    public void setMixinBytecode(InsnList insnList) {
        mixinMethodNode.instructions = insnList;
        mixinMethodNode.access &= ~Opcodes.ACC_ABSTRACT;
    }
}
