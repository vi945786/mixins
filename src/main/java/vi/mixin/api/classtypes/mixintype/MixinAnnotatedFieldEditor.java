package vi.mixin.api.classtypes.mixintype;

import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.editors.AnnotatedFieldEditor;

public class MixinAnnotatedFieldEditor extends AnnotatedFieldEditor {
    FieldInsnNode get = null;
    FieldInsnNode set = null;
    boolean copy = true;

    protected MixinAnnotatedFieldEditor(FieldNode mixinFieldNode, Object targetEditor) {
        super(mixinFieldNode, targetEditor);
    }

    public void changeGet(FieldInsnNode fieldInsnNode) {
        get = fieldInsnNode;
    }

    public void changeSet(FieldInsnNode fieldInsnNode) {
        set = fieldInsnNode;
    }

    public void doNotCopyToTargetClass() {
        copy = false;
    }
}
