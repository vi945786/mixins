package vi.mixin.api.transformers.mixintype;

import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.transformers.FieldEditor;
import vi.mixin.api.transformers.targeteditors.TargetFieldEditor;

public class MixinFieldEditor extends FieldEditor {
    FieldInsnNode get = null;
    FieldInsnNode set = null;
    boolean copy = true;

    MixinFieldEditor(MethodNode mixinMethodNode, TargetFieldEditor[] targetFieldEditors) {
        super(mixinMethodNode, targetFieldEditors);
    }

    MixinFieldEditor(FieldNode mixinFieldNode, TargetFieldEditor[] targetFieldEditors) {
        super(mixinFieldNode, targetFieldEditors);
    }

    public void changeGet(FieldInsnNode fieldInsnNode) {
        if(mixinFieldNode == null) throw new UnsupportedOperationException("MixinFieldEditor.changeGet is only for field editors with a field annotation");
        get = fieldInsnNode;
    }

    public void changeSet(FieldInsnNode fieldInsnNode) {
        if(mixinFieldNode == null) throw new UnsupportedOperationException("MixinFieldEditor.changeSet is only for field editors with a field annotation");
        set = fieldInsnNode;
    }

    public void doNotCopyToTarget() {
        if(mixinFieldNode == null) throw new UnsupportedOperationException("MixinFieldEditor.doNotCopyToTarget is only for field editors with a field annotation");
        copy = false;
    }

    public void makeTargetNonFinal(int index) {
        targetFieldEditors[index].makeNonFinal();
    }

    public void makeTargetPublic(int index) {
        targetFieldEditors[index].makePublic();
    }
}
