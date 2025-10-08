package vi.mixin.api.classtypes.mixintype;

import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import vi.mixin.api.editors.AnnotatedFieldEditor;

public class MixinAnnotatedFieldEditor extends AnnotatedFieldEditor {
    private final String realTargetClassName;
    private final String replaceName;

    FieldInsnNode get = null;
    FieldInsnNode set = null;
    boolean copy = true;

    protected MixinAnnotatedFieldEditor(FieldNode mixinFieldNode, Object targetEditor, String realTargetClassName, String replaceName) {
        super(mixinFieldNode, targetEditor);
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
