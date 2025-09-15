package vi.mixin.api.editors;

import org.objectweb.asm.tree.*;
import vi.mixin.api.injection.At;

import java.util.*;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.tree.AbstractInsnNode.*;

public class BytecodeEditor {

    private final int[] added;
    private final boolean[] removed;
    private final InsnList original;
    private final InsnList modified;

    private static InsnList cloneInsnList(InsnList original) {
        MethodNode mv = new MethodNode();
        original.accept(mv);
        return mv.instructions;
    }

    public BytecodeEditor(InsnList list) {
        this.modified = list;
        original = cloneInsnList(modified);
        added = new int[original.size() +1];
        removed = new boolean[original.size()];
    }

    public List<AbstractInsnNode> getBytecode() {
        return List.of(cloneInsnList(original).toArray());
    }

    public AbstractInsnNode get(int index) {
        return getBytecode().get(index);
    }

    public List<Integer> getAtTargetIndexes(At atAnnotation) {
         At.Location location = atAnnotation.value();
         List<Integer> ordinal = Arrays.stream(atAnnotation.ordinal()).boxed().toList();
         String target = atAnnotation.target();
         int opcode = atAnnotation.opcode();

         return switch (location) {
             case HEAD -> List.of(0);
             case RETURN ->
                     getInsnNodesIndexes(INSN, List.of(IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN), ordinal);
             case TAIL ->
                     Collections.singletonList(getInsnNodesIndexes(INSN, List.of(IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN), List.of()).getLast());
             case INVOKE -> {
                 int splitOwner = target.indexOf(';');
                 int splitName = target.indexOf('(');
                 String owner = target.substring(0, splitOwner);
                 String name = target.substring(splitOwner+1, splitName);
                 String desc = target.substring(splitName);
                 yield getInsnNodesIndexes(METHOD_INSN, List.of(INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE), ordinal, owner, name, desc);
             }
             case FIELD -> {
                 int splitOwner = target.indexOf(';');
                 String owner = target.substring(0, splitOwner);
                 String name = target.substring(splitOwner+1);
                 yield getInsnNodesIndexes(FIELD_INSN, opcode, ordinal, owner, name, null);
             }
             case NEW -> {
                 if(target.contains(")")) {
                     int splitOwner = target.indexOf(')');
                     String owner = target.substring(splitOwner+1);
                     String desc = target.substring(0, splitOwner+1) + "V";
                     yield getInsnNodesIndexes(METHOD_INSN, opcode, ordinal, owner, "<init>", desc);
                 } else {
                     yield getInsnNodesIndexes(METHOD_INSN, opcode, ordinal, target, "<init>", null);
                 }
             }
             case JUMP -> getInsnNodesIndexes(JUMP_INSN, opcode, ordinal, (Object) null);
         };
    }



    public List<Integer> getInsnNodesIndexes(int type, Integer opcode, Object... values) {
        return getInsnNodesIndexes(type, opcode == null ? null : List.of(opcode), List.of(), values);
    }

    public List<Integer> getInsnNodesIndexes(int type, Integer opcode, List<Integer> ordinals, Object... values) {
        return getInsnNodesIndexes(type, opcode == null ? null : List.of(opcode), ordinals, values);
    }

    public List<Integer> getInsnNodesIndexes(int type, List<Integer> opcodes, List<Integer> ordinals, Object... values) {
        List<Integer> indexes = new ArrayList<>();
        for(int i = 0; i < original.size(); i++) {
            AbstractInsnNode insnNode = original.get(i);
            if((insnNode.getType() != type && type != -1) || (opcodes != null && !opcodes.contains(insnNode.getOpcode()))) continue;

            if(equals(insnNode, values)) indexes.add(i);
        }

        if(ordinals == null || ordinals.isEmpty()) return indexes;
        List<Integer> newIndexes = new ArrayList<>();
        for (int i = 0; i < indexes.size(); i++) {
            if(ordinals.contains(i)) newIndexes.add(indexes.get(i));
        }

        return newIndexes;
    }


    public static boolean equals(AbstractInsnNode node, Object... values) {
        return switch (node) {
            case null -> false;
            case InsnNode insnNode -> true;
            case IntInsnNode n -> match(n.operand, values, 0);
            case VarInsnNode n -> match(n.var, values, 0);
            case TypeInsnNode n -> match(n.desc, values, 0);
            case FieldInsnNode n -> match(n.owner, values, 0)
                    && match(n.name, values, 1)
                    && match(n.desc, values, 2);
            case MethodInsnNode n -> match(n.owner, values, 0)
                    && match(n.name, values, 1)
                    && match(n.desc, values, 2)
                    && match(n.itf, values, 3);
            case InvokeDynamicInsnNode n -> match(n.name, values, 0)
                    && match(n.desc, values, 1)
                    && match(n.bsm, values, 2)
                    && matchArray(n.bsmArgs, values, 3);
            case JumpInsnNode n -> match(n.label, values, 0);
            case LabelNode n -> match(n.getLabel(), values, 0);
            case LdcInsnNode n -> match(n.cst, values, 0);
            case IincInsnNode n -> match(n.var, values, 0)
                    && match(n.incr, values, 1);
            case TableSwitchInsnNode n -> match(n.min, values, 0)
                    && match(n.max, values, 1)
                    && match(n.dflt, values, 2)
                    && match(n.labels, values, 3);
            case LookupSwitchInsnNode n -> match(n.dflt, values, 0)
                    && match(n.keys, values, 1)
                    && match(n.labels, values, 2);
            case MultiANewArrayInsnNode n -> match(n.desc, values, 0)
                    && match(n.dims, values, 1);
            case FrameNode n -> match(n.type, values, 0)
                    && match(n.local, values, 1)
                    && match(n.stack, values, 2);
            case LineNumberNode n -> match(n.line, values, 0)
                    && match(n.start, values, 1);
            default -> throw new IllegalStateException("Unsupported node type: " + node.getClass());
        };

    }

    private static boolean match(Object actual, Object[] values, int index) {
        if (index >= values.length) return true; // not provided → ignore
        Object expected = values[index];
        return expected == null || Objects.equals(actual, expected);
    }

    private static boolean matchArray(Object[] actual, Object[] values, int index) {
        if (index >= values.length) return true;
        Object expected = values[index];
        if (expected == null) return true;
        if (!(expected instanceof Object[] expArr)) return false;
        return Arrays.equals(actual, expArr);
    }

    private int getUpdatedIndex(int index) {
        int newIndex = index;
        for (int i = 0; i <= index; i++) {
            newIndex += added[i];
        }

        return newIndex;
    }

    public void remove(int index) {
        if(removed[index]) return;
        modified.remove(modified.get(getUpdatedIndex(index)));
        added[index]--;
        removed[index] = true;
    }

    /**
     * append
     */
    public void add(AbstractInsnNode... add) {
        add(Arrays.asList(add));
    }

    /**
     * append
     */
    public void add(List<AbstractInsnNode> add) {
        add(original.size(), add);
    }

    /**
     * inserts before index
     */
    public void add(int index, AbstractInsnNode... add) {
        add(index, Arrays.asList(add));
    }

    /**
     * inserts before index
     */
    public void add(int index, List<AbstractInsnNode> add) {
        if (index == original.size() || getUpdatedIndex(index) >= modified.size() || getUpdatedIndex(index) == -1) {
            for(AbstractInsnNode node : add) {
                modified.add(node);
            }
        } else if(0 <= index && index < original.size()) {
            AbstractInsnNode addNode = modified.get(getUpdatedIndex(index));
            for (AbstractInsnNode node : add) {
                if(removed[index]) modified.insert(addNode, node);
                else modified.insertBefore(addNode, node);
            }
        }
        added[index] += add.size();
    }
}
