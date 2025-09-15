package vi.mixin.api.editors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;

import java.util.ArrayList;
import java.util.List;

public class FieldEditor {
    final FieldNode fieldNode;
    private final List<AnnotationEditor> annotationEditors;
    private final int access;

    public FieldEditor(FieldNode fieldNode) {
        this.fieldNode = fieldNode;
        annotationEditors = fieldNode.invisibleAnnotations == null ? new ArrayList<>() : fieldNode.invisibleAnnotations.stream().map(AnnotationEditor::new).toList();
        access = fieldNode.access;
    }

    public FieldEditor makePublic() {
        fieldNode.access |= Opcodes.ACC_PUBLIC;
        fieldNode.access &= ~Opcodes.ACC_PRIVATE;
        fieldNode.access &= ~Opcodes.ACC_PROTECTED;
        return this;
    }

    public FieldEditor makeNonFinal() {
        fieldNode.access &= ~Opcodes.ACC_FINAL;
        return this;
    }

    public List<AnnotationEditor> getAnnotationEditors() {
        return List.copyOf(annotationEditors);
    }

    public AnnotationEditor getAnnotationEditor(String desc) {
        return annotationEditors.stream().filter(annotationEditor -> annotationEditor.getDesc().equals(desc)).findFirst().orElse(null);
    }

    public String getName() {
        return fieldNode.name;
    }

    public String getDesc() {
        return fieldNode.desc;
    }

    public int getAccess() {
        return access;
    }

    public int setAccess(int access) {
        return fieldNode.access = access;
    }

    /**
     * if the default value isn't primitive, Class or String it is not a default value.
     * it is set in the <init> or <clinit> method
     */
    public Object getDefaultValue() {
        return fieldNode.value;
    }

    /**
     * only applies to generic types
     */
    public String getSignature() {
        return fieldNode.signature;
    }
}
