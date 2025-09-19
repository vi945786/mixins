package vi.mixin.api.classtypes.accessortype;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.editors.AnnotatedMethodEditor;

public class AccessorAnnotatedMethodEditor extends AnnotatedMethodEditor {
    protected AccessorAnnotatedMethodEditor(MethodNode mixinMethodNode) {
        super(mixinMethodNode);
    }

     public void setBytecode(InsnList insnList) {
        mixinMethodNode.instructions = insnList;
        mixinMethodNode.access &= ~Opcodes.ACC_ABSTRACT;
    }
}
