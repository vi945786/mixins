package vi.mixin.api.editors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import vi.mixin.bytecode.Mixiner;

import java.util.*;

public class ClassEditor {
    final ClassNode classNode;
    private final List<String> interfaces;
    private final ClassEditor outerClass;
    private final List<ClassEditor> innerClasses;
    private final List<AnnotationEditor> annotationEditors;
    private final List<MethodEditor> methodEditors;
    private final List<FieldEditor> fieldEditors;
    private final int access;
    private final String superName;
    private final Class<?> c;

    public ClassEditor(ClassNode classNode, Class<?> c, ClassEditor outerClass, List<ClassEditor> innerClasses) {
        this.classNode = classNode;
        this.outerClass = outerClass;
        this.innerClasses = innerClasses;
        annotationEditors = classNode.invisibleAnnotations == null ? new ArrayList<>() : classNode.invisibleAnnotations.stream().map(AnnotationEditor::new).toList();
        methodEditors = classNode.methods.stream().map(MethodEditor::new).toList();
        fieldEditors = classNode.fields.stream().map(FieldEditor::new).toList();
        interfaces = List.copyOf(classNode.interfaces);
        access = classNode.access;
        superName = classNode.superName;
        this.c = c;
    }

    /**
     * only works for mixin classes
     */
    public Set<ClassEditor> getAllClassesInHierarchy() {
    return getAllClassesInHierarchy(new HashSet<>());
}

    private Set<ClassEditor> getAllClassesInHierarchy(Set<ClassEditor> visited) {
        if (visited.contains(this)) return Collections.emptySet();

        visited.add(this);
        Set<ClassEditor> classEditors = new HashSet<>();
        classEditors.add(this);

        if (outerClass != null) classEditors.addAll(outerClass.getAllClassesInHierarchy(visited));
        innerClasses.forEach(inner -> classEditors.addAll(inner.getAllClassesInHierarchy(visited)));

        return classEditors;
    }

    /**
     * only works for target classes
     */
    public Class<?> getRealClass() {
        return c;
    }

    /**
     * only works for mixin classes
     */
    public ClassEditor getOuterClass() {
        return outerClass;
    }

    /**
     * only works for mixin classes
     */
    public List<ClassEditor> getInnerClasses() {
        return List.copyOf(innerClasses);
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

    public Class<?> getTargetClass() {
        return Mixiner.getTargetClass(this);
    }

    public List<AnnotationEditor> getAnnotationEditors() {
        return List.copyOf(annotationEditors);
    }

    public AnnotationEditor getAnnotationEditor(String desc) {
        return annotationEditors.stream().filter(annotationEditor -> annotationEditor.getDesc().equals(desc)).findFirst().orElse(null);
    }

    public List<MethodEditor> getMethodEditors() {
        return List.copyOf(methodEditors);
    }

    public List<MethodEditor> getMethodEditors(String name) {
        return methodEditors.stream().filter(method -> method.getName().equals(name)).toList();
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

    public int setAccess(int access) {
        return classNode.access = access;
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
