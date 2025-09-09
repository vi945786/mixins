package vi.mixin.api.editors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

public class ClassEditor {
    final ClassNode classNode;
    private final List<String> interfaces;
    private final List<MethodEditor> methodEditors;
    private final List<FieldEditor> fieldEditors;
    private final int access;
    private final String superName;

    public ClassEditor(ClassNode classNode) {
        this.classNode = classNode;
        methodEditors = classNode.methods.stream().map(MethodEditor::new).toList();
        fieldEditors = classNode.fields.stream().map(FieldEditor::new).toList();
        interfaces = List.copyOf(classNode.interfaces);
        access = classNode.access;
        superName = classNode.superName;
    }

    public ClassEditor makePublic() {
        classNode.access |= Opcodes.ACC_PUBLIC;
        classNode.access &= ~Opcodes.ACC_PRIVATE;
        classNode.access &= ~Opcodes.ACC_PROTECTED;
        return this;
    }

    public ClassEditor makeNonFinalOrSealed() {
        classNode.access &= ~Opcodes.ACC_FINAL;
        classNode.permittedSubclasses = null;
        return this;
    }

    public ClassEditor makeNonAbstract() {
        classNode.access &= ~Opcodes.ACC_ABSTRACT;
        return this;
    }

    public ClassEditor addMethod(MethodEditor editor) {
        classNode.methods.add(editor.methodNode);
        return this;
    }

    public ClassEditor addField(FieldEditor editor) {
        classNode.fields.add(editor.fieldNode);
        return this;
    }

    public ClassEditor removeMethod(MethodEditor editor) {
        classNode.methods.remove(editor.methodNode);
        return this;
    }

    public ClassEditor removeField(FieldEditor editor) {
        classNode.fields.remove(editor.fieldNode);
        return this;
    }

    public List<MethodEditor> getMethodEditors() {
        return List.copyOf(methodEditors);
    }

    public MethodEditor getMethodEditor(String nameAndDesc) {
        return methodEditors.stream().filter(method -> (method.getName() + method.getDesc()).equals(nameAndDesc)).findAny().orElse(null);
    }

    public List<FieldEditor> getFieldEditors() {
        return List.copyOf(fieldEditors);
    }

    public FieldEditor getFieldEditor(String name) {
        return fieldEditors.stream().filter(field -> (field.getName()).equals(name)).findAny().orElse(null);
    }

    public ClassEditor addInterface(String name) {
        classNode.interfaces.add(name.replace(".", "/"));
        return this;
    }

    public String getName() {
        return classNode.name;
    }

    public int getAccess() {
        return access;
    }

    /**
     * only applies to generic types
     */
    public String getSignature() {
        return classNode.signature;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public String getSuperName() {
        return superName;
    }

    /**
     * this only works on mixin classes! it will crash for target classes!
     */
    public String setSuperName(String superName) {
        return classNode.superName = superName;
    }
}
