package vi.mixin.api.classtypes.extendertype;

import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.editors.AnnotatedFieldEditor;

public class ExtenderAnnotatedFieldEditor extends AnnotatedFieldEditor {
    FieldInsnNode get = null;
    FieldInsnNode set = null;
    boolean delete = false;

    protected ExtenderAnnotatedFieldEditor(FieldNode mixinFieldNode) {
        super(mixinFieldNode);
    }

    public void changeGet(FieldInsnNode fieldInsnNode) {
        get = fieldInsnNode;
    }

    public void changeSet(FieldInsnNode fieldInsnNode) {
        set = fieldInsnNode;
    }

    public void delete() {
        delete = true;
    }
}
