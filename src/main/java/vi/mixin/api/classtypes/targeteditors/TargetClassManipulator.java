package vi.mixin.api.classtypes.targeteditors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TargetClassManipulator {
    private final TargetInsnListManipulator.OpcodeStates opcodeStates;

    private final ClassNode modified;
    private final ClassNode original;

    Map<String, MethodNode> originalMethodNodes = new HashMap<>();
    Map<String, FieldNode> originalFieldNodes = new HashMap<>();

    public TargetClassManipulator(ClassNode classNode, ClassNode original, TargetInsnListManipulator.OpcodeStates opcodeStates) {
        this.opcodeStates = opcodeStates;

        this.modified = classNode;
        this.original = new ClassNode();
        original.accept(this.original);

        this.original.methods.forEach(methodNode -> originalMethodNodes.put(methodNode.name + methodNode.desc, methodNode));
        this.original.fields.forEach(fieldNode -> originalFieldNodes.put(fieldNode.name, fieldNode));
    }

    public ClassNode getClassNodeClone() {
        ClassNode clone = new ClassNode();
        original.accept(clone);
        return clone;
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

        MethodNode cloneMethodNode = new TargetMethodManipulator(original.name, methodNode, methodNode, opcodeStates).getMethodNodeClone();
        original.methods.add(cloneMethodNode);
        originalMethodNodes.put(cloneMethodNode.name + cloneMethodNode.desc, cloneMethodNode);
    }

    public List<TargetMethodManipulator> getMethodManipulators() {
        return modified.methods.stream().map(methodNode -> new TargetMethodManipulator(original.name, methodNode, originalMethodNodes.get(methodNode.name + methodNode.desc), opcodeStates)).toList();
    }

    @SuppressWarnings("unused")
    public List<TargetMethodManipulator> getMethodManipulators(String name) {
        return modified.methods.stream().filter(methodNode -> methodNode.name.equals(name)).map(methodNode -> new TargetMethodManipulator(original.name, methodNode, originalMethodNodes.get(methodNode.name + methodNode.desc), opcodeStates)).toList();
    }

    public TargetMethodManipulator getMethodManipulator(String nameAndDesc) {
        String name = nameAndDesc.split("\\(")[0];
        String desc = "(" + nameAndDesc.split("\\(")[1];

        return modified.methods.stream().filter(methodNode -> {
            if(!methodNode.name.equals(name)) return false;

            Type[] searchArgumentTypes = Type.getArgumentTypes(desc);
            Type[] targetArgumentTypes = Type.getArgumentTypes(methodNode.desc);
            if(searchArgumentTypes.length != targetArgumentTypes.length) return false;
            for (int i = 0; i < targetArgumentTypes.length; i++) {
                if (!searchArgumentTypes[i].equals(targetArgumentTypes[i]) && !searchArgumentTypes[i].equals(Type.getType(Object.class))) return false;
            }

            return true;
        }).map(methodNode -> new TargetMethodManipulator(original.name, methodNode, originalMethodNodes.get(methodNode.name + methodNode.desc), opcodeStates)).findAny().orElse(null);
    }

    public void addField(FieldNode fieldNode) {
        modified.fields.add(fieldNode);

        FieldNode cloneFieldNode = new TargetFieldManipulator(fieldNode, fieldNode).getFieldNodeClone();
        original.fields.add(cloneFieldNode);
        originalFieldNodes.put(cloneFieldNode.name, cloneFieldNode);
    }

    @SuppressWarnings("unused")
    public List<TargetFieldManipulator> getFieldManipulators() {
        return modified.fields.stream().map(fieldNode -> new TargetFieldManipulator(fieldNode, originalFieldNodes.get(fieldNode.name))).toList();
    }

    public TargetFieldManipulator getFieldManipulator(String name) {
        return modified.fields.stream().filter(fieldNode -> fieldNode.name.equals(name)).map(fieldNode -> new TargetFieldManipulator(fieldNode, originalFieldNodes.get(fieldNode.name))).findAny().orElse(null);
    }
}
