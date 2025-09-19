package vi.mixin.api.classtypes.targeteditors;

import org.objectweb.asm.tree.*;
import vi.mixin.bytecode.Mixiner;

import java.lang.reflect.Field;
import java.util.*;

public class MixinClassTargetInsnListEditor {
    private static final IdentityHashMap<InsnList, List<OpcodeState>> isOriginalOpcode;

    private enum OpcodeState {
        ORIGINAL,
        INJECTED,
        DELETED
    }

    static {
        if("main".equals(System.getProperty("mixin.stage"))) {
            try {
                Field isOriginalOpcodeField = Class.forName(Mixiner.class.getName(), false, ClassLoader.getSystemClassLoader()).getDeclaredField("isOriginalOpcode");
                isOriginalOpcodeField.setAccessible(true);
                isOriginalOpcode = (IdentityHashMap<InsnList, List<OpcodeState>>) isOriginalOpcodeField.get(null);
            } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            isOriginalOpcode = new IdentityHashMap<>();
        }
    }

    private final InsnList modified;
    private final InsnList original;

    public MixinClassTargetInsnListEditor(InsnList insnList, InsnList original) {
        this.modified = insnList;
        this.original = original;

        if(!isOriginalOpcode.containsKey(modified)) {
            List<OpcodeState> isOriginalList = new ArrayList<>();
            isOriginalOpcode.put(modified, isOriginalList);
            for(AbstractInsnNode node : modified) {
                isOriginalList.add(OpcodeState.ORIGINAL);
            }
        }
    }

    public InsnList getInsnListClone() {
        MethodNode clone = new MethodNode();
        original.accept(clone);
        return clone.instructions;
    }

    private int getUpdatedIndex(int index) {
        int modifiedIndex = index;
        int originals = 0;
        List<OpcodeState> isOriginalList = isOriginalOpcode.get(modified);
        for (int i = 0; originals < index; i++) {
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
        List<OpcodeState> isOriginalList = isOriginalOpcode.get(modified);
        for (int i = 0; originals < index; i++) {
            switch (isOriginalList.get(i)) {
                case ORIGINAL, DELETED -> originals++;
                case INJECTED -> modifiedIndex++;
            }
        }

        return isOriginalList.get(modifiedIndex) == OpcodeState.DELETED;
    }

    public void add(AbstractInsnNode node) {
        modified.add(node);
        isOriginalOpcode.get(modified).add(OpcodeState.INJECTED);
    }

    public void add(InsnList list) {
        modified.add(list);
        List<OpcodeState> isOriginalList = isOriginalOpcode.get(modified);
        for(AbstractInsnNode node : list) isOriginalList.add(OpcodeState.INJECTED);
    }

    public void insertBefore(int index, AbstractInsnNode node) {
        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = isOriginalOpcode.get(modified);

        modified.insertBefore(modified.get(modifiedIndex), node);
        isOriginalList.add(modifiedIndex, OpcodeState.INJECTED);
    }

    public void insertBefore(int index, InsnList list) {
        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = isOriginalOpcode.get(modified);

        modified.insertBefore(modified.get(modifiedIndex), list);
        for(AbstractInsnNode node : list) isOriginalList.add(modifiedIndex, OpcodeState.INJECTED);
    }

    public void remove(int index) {
        if(isDeleted(index)) return;

        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = isOriginalOpcode.get(modified);

        modified.remove(modified.get(modifiedIndex));
        isOriginalList.set(modifiedIndex, OpcodeState.DELETED);
    }

    public void insertAfter(int index, AbstractInsnNode node) {
        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = isOriginalOpcode.get(modified);

        modified.insert(modified.get(modifiedIndex), node);
        isOriginalList.add(modifiedIndex+1, OpcodeState.INJECTED);
    }

    public void insertAfter(int index, InsnList list) {
        int modifiedIndex = getUpdatedIndex(index);
        List<OpcodeState> isOriginalList = isOriginalOpcode.get(modified);

        modified.insert(modified.get(modifiedIndex), list);
        for(AbstractInsnNode node : list) isOriginalList.add(modifiedIndex+1, OpcodeState.INJECTED);
    }
}
