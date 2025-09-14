package vi.mixin.api.editors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

public class MethodEditor {
    final MethodNode methodNode;
    private final List<AnnotationEditor> annotationEditors;
    private final List<String> exceptions;
    private final BytecodeEditor bytecodeEditor;
    private final String desc;
    private final String signature;
    private final int access;

    public MethodEditor(MethodNode methodNode) {
        this.methodNode = methodNode;
        annotationEditors = methodNode.invisibleAnnotations == null ? new ArrayList<>() : methodNode.invisibleAnnotations.stream().map(AnnotationEditor::new).toList();
        bytecodeEditor = new BytecodeEditor(methodNode.instructions);
        exceptions = List.copyOf(methodNode.exceptions);
        desc = methodNode.desc;
        signature = methodNode.signature;
        access = methodNode.access;
    }

    public MethodEditor makePublic() {
        methodNode.access |= Opcodes.ACC_PUBLIC;
        methodNode.access &= ~Opcodes.ACC_PRIVATE;
        methodNode.access &= ~Opcodes.ACC_PROTECTED;
        return this;
    }

    public MethodEditor makeNonFinal() {
        methodNode.access &= ~Opcodes.ACC_FINAL;
        return this;
    }

    /**
     * be careful with this
     */
    public MethodEditor makeStatic() {
        methodNode.access |= Opcodes.ACC_STATIC;
        return this;
    }

    /**
     * be careful with this
     * make sure to set the signature too
     */
    public MethodEditor setDesc(String desc) {
        methodNode.desc = desc;
        return this;
    }

    /**
     * be careful with this
     * make sure to set the desc too
     */
    public MethodEditor setSignature(String signature) {
        methodNode.signature = signature;
        return this;
    }

    public List<AnnotationEditor> getAnnotationEditor() {
        return List.copyOf(annotationEditors);
    }

    public AnnotationEditor getAnnotationEditor(String desc) {
        return annotationEditors.stream().filter(annotationEditor -> annotationEditor.getDesc().equals(desc)).findFirst().orElse(null);
    }

    public MethodEditor makeNonAbstract() {
        methodNode.access &= ~Opcodes.ACC_ABSTRACT;
        return this;
    }

    public MethodEditor addException(String exception) {
        methodNode.exceptions.add(exception);
        return this;
    }

    public BytecodeEditor getBytecodeEditor() {
        return bytecodeEditor;
    }

    public String getName() {
        return methodNode.name;
    }

    public int getAccess() {
        return access;
    }

    public int setAccess(int access) {
        return methodNode.access = access;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * only applies to generic types
     */
    public String getSignature() {
        return signature;
    }

    public List<String> getExceptions() {
        return exceptions;
    }
}
