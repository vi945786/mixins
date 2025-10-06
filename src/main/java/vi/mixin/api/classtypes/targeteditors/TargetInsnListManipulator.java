package vi.mixin.api.classtypes.targeteditors;

import org.objectweb.asm.tree.*;
import java.util.*;

public class TargetInsnListManipulator {
    public static class OpcodeStates {
        final HashMap<String, List<OpcodeState>> OpcodeStates = new HashMap<>();
    }

    private final OpcodeStates opcodeStates;

    private enum OpcodeState {
        ORIGINAL,
        INJECTED,
        DELETED
    }

    private final String id;
    private final InsnList modified;
    private final InsnList original;

    public TargetInsnListManipulator(String id, InsnList insnList, InsnList original, OpcodeStates opcodeStates) {
        this.opcodeStates = opcodeStates;

        this.id = id;
        this.modified = insnList;

        MethodNode clone = new MethodNode();
        original.accept(clone);
        this.original = clone.instructions;

        if(!opcodeStates.OpcodeStates.containsKey(id)) {
            List<OpcodeState> isOriginalList = new ArrayList<>();
            opcodeStates.OpcodeStates.put(id, isOriginalList);
            for (int i = 0; i < modified.size(); i++) {
                isOriginalList.add(OpcodeState.ORIGINAL);
            }
        }
    }

    public InsnList getInsnListClone() {
        MethodNode clone = new MethodNode();
        original.accept(clone);

        for (int i = 0; i < original.size(); i++) {
            if(clone.instructions.get(i) instanceof LabelNode labelNode) {
                originalLabelMap.put(labelNode, (LabelNode) original.get(i));
            }
        }

        return clone.instructions;
    }

    HashMap<LabelNode, LabelNode> originalLabelMap = new HashMap<>();
    private InsnList cloneToOriginal(InsnList list) {
        Arrays.stream(list.toArray())
                .filter(node -> node instanceof LabelNode labelNode && !originalLabelMap.containsKey(labelNode))
                .forEach(node -> originalLabelMap.put((LabelNode) node, new LabelNode()));

        InsnList clone = new InsnList();
        Arrays.stream(list.toArray()).forEach(node -> clone.add(node.clone(originalLabelMap)));

        return clone;
    }

    @SuppressWarnings("unused")
    private AbstractInsnNode cloneToOriginal(AbstractInsnNode node) {
        InsnList list = new InsnList();
        list.add(node);

        return cloneToOriginal(list).getFirst();
    }

    HashMap<LabelNode, LabelNode> originalToModifiedLabelMap = new HashMap<>();
    private InsnList cloneToModified(InsnList list) {
        list = cloneToOriginal(list);
        Arrays.stream(list.toArray())
                .filter(node -> node instanceof LabelNode labelNode && !originalToModifiedLabelMap.containsKey(labelNode))
                .forEach(node -> originalToModifiedLabelMap.put((LabelNode) node, new LabelNode()));

        InsnList clone = new InsnList();
        Arrays.stream(list.toArray()).forEach(node -> clone.add(node.clone(originalToModifiedLabelMap)));

        return clone;
    }

    private AbstractInsnNode cloneToModified(AbstractInsnNode node) {
        InsnList list = new InsnList();
        list.add(node);

        return cloneToModified(list).getFirst();
    }

    private int getUpdatedIndex(int index) {
        int modifiedIndex = index;
        int originals = 0;
        List<OpcodeState> isOriginalList = opcodeStates.OpcodeStates.get(id);
        for (int i = 0; originals <= index; i++) {
            switch (isOriginalList.get(i)) {
                case ORIGINAL -> originals++;
                case INJECTED -> modifiedIndex++;
                case DELETED -> {}
            }
        }

        return modifiedIndex;
    }

    private boolean isDeleted(int index) {
        int modifiedIndex = index;
        int originals = 0;
        List<OpcodeState> isOriginalList = opcodeStates.OpcodeStates.get(id);
        for (int i = 0; originals < index; i++) {
            switch (isOriginalList.get(i)) {
                case ORIGINAL, DELETED -> originals++;
                case INJECTED -> modifiedIndex++;
            }
        }

        return isOriginalList.get(modifiedIndex) == OpcodeState.DELETED;
    }

    public void add(AbstractInsnNode node) {
        modified.add(cloneToModified(node));
        opcodeStates.OpcodeStates.get(id).add(OpcodeState.INJECTED);
    }

    public void add(InsnList list) {
        modified.add(cloneToModified(list));
        List<OpcodeState> isOriginalList = opcodeStates.OpcodeStates.get(id);
        for (int i = 0; i < list.size(); i++) isOriginalList.add(OpcodeState.INJECTED);
    }

    public void insertBefore(int index, AbstractInsnNode node) {
        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = opcodeStates.OpcodeStates.get(id);

        modified.insertBefore(modified.get(modifiedIndex), cloneToModified(node));
        isOriginalList.add(modifiedIndex, OpcodeState.INJECTED);
    }

    public void insertBefore(int index, InsnList list) {
        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = opcodeStates.OpcodeStates.get(id);

        modified.insertBefore(modified.get(modifiedIndex), cloneToModified(list));
        for (int i = 0; i < list.size(); i++) {
            isOriginalList.add(modifiedIndex, OpcodeState.INJECTED);
        }
    }

    public void remove(int index) {
        if(isDeleted(index)) return;

        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = opcodeStates.OpcodeStates.get(id);

        modified.remove(modified.get(modifiedIndex));
        isOriginalList.set(modifiedIndex, OpcodeState.DELETED);
    }

    @SuppressWarnings("unused")
    public void insertAfter(int index, AbstractInsnNode node) {
        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = opcodeStates.OpcodeStates.get(id);

        modified.insert(modified.get(modifiedIndex), cloneToModified(node));
        isOriginalList.add(modifiedIndex+1, OpcodeState.INJECTED);
    }

    @SuppressWarnings("unused")
    public void insertAfter(int index, InsnList list) {
        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = opcodeStates.OpcodeStates.get(id);

        modified.insert(modified.get(modifiedIndex), cloneToModified(list));
        for (int i = 0; i < list.size(); i++) {
            isOriginalList.add(modifiedIndex+1, OpcodeState.INJECTED);
        }
    }
}
