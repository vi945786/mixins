package vi.mixin.api.classtypes.targeteditors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MixinClassTargetClassEditor {
    private final ClassNode modified;
    private final ClassNode original;
    private final Class<?> c;

    Map<String, MethodNode> originalMethodNodes = new HashMap<>();
    Map<String, FieldNode> originalFieldNodes = new HashMap<>();

    public MixinClassTargetClassEditor(ClassNode classNode, ClassNode original, Class<?> c) {
        this.modified = classNode;
        this.original = new ClassNode();
        original.accept(this.original);
        this.c = c;

        this.original.methods.forEach(methodNode -> originalMethodNodes.put(methodNode.name + methodNode.desc, methodNode));
        this.original.fields.forEach(fieldNode -> originalFieldNodes.put(fieldNode.name, fieldNode));
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

        MethodNode cloneMethodNode = new MixinClassTargetMethodEditor(original.name, methodNode, methodNode).getMethodNodeClone();
        original.methods.add(cloneMethodNode);
        originalMethodNodes.put(cloneMethodNode.name + cloneMethodNode.desc, cloneMethodNode);
    }

    public List<MixinClassTargetMethodEditor> getMethodEditors() {
        return modified.methods.stream().map(methodNode -> new MixinClassTargetMethodEditor(original.name, methodNode, originalMethodNodes.get(methodNode.name + methodNode.desc))).toList();
    }

    @SuppressWarnings("unused")
    public List<MixinClassTargetMethodEditor> getMethodEditors(String name) {
        return modified.methods.stream().filter(methodNode -> methodNode.name.equals(name)).map(methodNode -> new MixinClassTargetMethodEditor(original.name, methodNode, originalMethodNodes.get(methodNode.name + methodNode.desc))).toList();
    }

    public MixinClassTargetMethodEditor getMethodEditor(String nameAndDesc) {
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
        }).map(methodNode -> new MixinClassTargetMethodEditor(original.name, methodNode, originalMethodNodes.get(methodNode.name + methodNode.desc))).findAny().orElse(null);
    }

    public void addField(FieldNode fieldNode) {
        modified.fields.add(fieldNode);

        FieldNode cloneFieldNode = new MixinClassTargetFieldEditor(fieldNode, fieldNode).getFieldNodeClone();
        original.fields.add(cloneFieldNode);
        originalFieldNodes.put(cloneFieldNode.name, cloneFieldNode);
    }

    @SuppressWarnings("unused")
    public List<MixinClassTargetFieldEditor> getFieldEditors() {
        return modified.fields.stream().map(fieldNode -> new MixinClassTargetFieldEditor(fieldNode, originalFieldNodes.get(fieldNode.name))).toList();
    }

    public MixinClassTargetFieldEditor getFieldEditor(String name) {
        return modified.fields.stream().filter(fieldNode -> fieldNode.name.equals(name)).map(fieldNode -> new MixinClassTargetFieldEditor(fieldNode, originalFieldNodes.get(fieldNode.name))).findAny().orElse(null);
    }
}
