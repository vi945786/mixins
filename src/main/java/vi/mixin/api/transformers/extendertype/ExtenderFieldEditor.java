package vi.mixin.api.transformers.extendertype;

import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.transformers.FieldEditor;
import vi.mixin.api.transformers.targeteditors.TargetFieldEditor;

public class ExtenderFieldEditor extends FieldEditor {
    FieldInsnNode get = null;
    FieldInsnNode set = null;
    boolean delete = false;

    ExtenderFieldEditor(MethodNode mixinMethodNode, TargetFieldEditor[] targetFieldEditors) {
        super(mixinMethodNode, targetFieldEditors);
    }

    ExtenderFieldEditor(FieldNode mixinFieldNode, TargetFieldEditor[] targetFieldEditors) {
        super(mixinFieldNode, targetFieldEditors);
    }

    public void changeGet(FieldInsnNode fieldInsnNode) {
        if(mixinFieldNode == null) throw new UnsupportedOperationException("ExtenderFieldEditor.changeGet is only for field editors with a field annotation");
        get = fieldInsnNode;
    }

    public void changeSet(FieldInsnNode fieldInsnNode) {
        if(mixinFieldNode == null) throw new UnsupportedOperationException("ExtenderFieldEditor.changeSet is only for field editors with a field annotation");
        set = fieldInsnNode;
    }

    public void delete() {
        if(mixinFieldNode == null) throw new UnsupportedOperationException("ExtenderFieldEditor.delete is only for method editors with a method annotation");
        delete = true;
    }

    public void makeTargetNonFinal(int index) {
        targetFieldEditors[index].makeNonFinal();
    }

    public void makeTargetPublic(int index) {
        targetFieldEditors[index].makePublic();
    }
}
