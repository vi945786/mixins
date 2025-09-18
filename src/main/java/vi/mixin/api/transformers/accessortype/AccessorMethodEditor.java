package vi.mixin.api.transformers.accessortype;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import vi.mixin.api.transformers.MethodEditor;
import vi.mixin.api.transformers.targeteditors.TargetMethodEditor;

public class AccessorMethodEditor extends MethodEditor {

    AccessorMethodEditor(MethodNode mixinMethodNode, TargetMethodEditor[] targetMethodEditors) {
        super(mixinMethodNode, targetMethodEditors);
    }

    AccessorMethodEditor(FieldNode mixinFieldNode, TargetMethodEditor[] targetMethodEditors) {
        super(mixinFieldNode, targetMethodEditors);
    }

    public void makeTargetPublic(int index) {
        targetMethodEditors[index].makePublic();
    }

    public void setMixinBytecode(InsnList insnList) {
        mixinMethodNode.instructions = insnList;
        mixinMethodNode.access &= ~Opcodes.ACC_ABSTRACT;
    }
}
