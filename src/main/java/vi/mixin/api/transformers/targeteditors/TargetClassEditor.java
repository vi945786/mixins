package vi.mixin.api.transformers.targeteditors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.transformers.FieldEditor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TargetClassEditor {
    private final ClassNode modified;
    private final ClassNode original;
    private final Class<?> c;

    Map<String, MethodNode> originalMethodNodes = new HashMap<>();
    Map<String, FieldNode> originalFieldNodes = new HashMap<>();

    public TargetClassEditor(ClassNode classNode, ClassNode original, Class<?> c) {
        this.modified = classNode;
        this.original = new ClassNode();
        original.accept(this.original);
        this.c = c;

        original.methods.forEach(methodNode -> originalMethodNodes.put(methodNode.name + methodNode.desc, methodNode));
        original.fields.forEach(fieldNode -> originalFieldNodes.put(fieldNode.name, fieldNode));
    }

    public ClassNode getClassNodeClone() {
        ClassNode clone = new ClassNode();
        original.accept(clone);
        return clone;
    }

    public Class<?> getRealClass() {
        return c;
    }

    public void makePublic() {
        modified.access |= Opcodes.ACC_PUBLIC;
        modified.access &= ~Opcodes.ACC_PRIVATE;
        modified.access &= ~Opcodes.ACC_PROTECTED;

        original.access |= Opcodes.ACC_PUBLIC;
        original.access &= ~Opcodes.ACC_PRIVATE;
        original.access &= ~Opcodes.ACC_PROTECTED;
    }

    public void makeNonFinalOrSealed() {
        modified.access &= ~Opcodes.ACC_FINAL;
        modified.permittedSubclasses = null;

        original.access &= ~Opcodes.ACC_FINAL;
        original.permittedSubclasses = null;
    }

    public void addInterface(String name) {
        modified.interfaces.add(name.replace(".", "/"));

        original.interfaces.add(name.replace(".", "/"));
    }

    public void addMethod(MethodNode methodNode) {
        modified.methods.add(methodNode);

        MethodNode cloneMethodNode = new TargetMethodEditor(methodNode, methodNode).getMethodNodeClone();
        original.methods.add(cloneMethodNode);
        originalMethodNodes.put(cloneMethodNode.name + cloneMethodNode.desc, cloneMethodNode);
    }

    public List<TargetMethodEditor> getMethodEditors() {
        return modified.methods.stream().map(methodNode -> new TargetMethodEditor(methodNode, originalMethodNodes.get(methodNode.name + methodNode.desc))).toList();
    }

    public List<TargetMethodEditor> getMethodEditors(String name) {
        return modified.methods.stream().filter(methodNode -> methodNode.name.equals(name)).map(methodNode -> new TargetMethodEditor(methodNode, originalMethodNodes.get(methodNode.name + methodNode.desc))).toList();
    }

    public TargetMethodEditor getMethodEditor(String nameAndDesc) {
        return modified.methods.stream().filter(methodNode -> (methodNode.name + methodNode.desc).equals(nameAndDesc)).map(methodNode -> new TargetMethodEditor(methodNode, originalMethodNodes.get(methodNode.name + methodNode.desc))).findAny().orElse(null);
    }

    public void addField(FieldNode fieldNode) {
        modified.fields.add(fieldNode);

        FieldNode cloneFieldNode = new TargetFieldEditor(fieldNode, fieldNode).getFieldNodeClone();
        original.fields.add(cloneFieldNode);
        originalFieldNodes.put(cloneFieldNode.name, cloneFieldNode);
    }

    public List<TargetFieldEditor> getFieldEditors() {
        return modified.fields.stream().map(fieldNode -> new TargetFieldEditor(fieldNode, originalFieldNodes.get(fieldNode.name))).toList();
    }

    public TargetFieldEditor getFieldEditor(String name) {
        return modified.fields.stream().filter(fieldNode -> fieldNode.name.equals(name)).map(fieldNode -> new TargetFieldEditor(fieldNode, originalFieldNodes.get(fieldNode.name))).findAny().orElse(null);
    }
}
