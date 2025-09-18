package vi.mixin.test;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import vi.mixin.api.injection.At;
import vi.mixin.api.transformers.TransformerHelper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AtAnnotationTest {

    @Test
    public void testAtHead() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new InsnNode(Opcodes.NOP));
        At at = new At() {
            @Override public int[] ordinal() { return new int[0]; }
            @Override public At.Location value() { return At.Location.HEAD; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return ""; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at);
        assertEquals(List.of(list.get(0)), nodes);
    }

    @Test
    public void testAtReturn() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new InsnNode(Opcodes.IRETURN));
        list.add(new InsnNode(Opcodes.LRETURN));
        list.add(new InsnNode(Opcodes.NOP));
        At at = new At() {
            @Override public int[] ordinal() { return new int[0]; }
            @Override public At.Location value() { return At.Location.RETURN; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return ""; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at);
        assertEquals(List.of(list.get(1), list.get(2)), nodes);
    }

    @Test
    public void testAtTail() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new InsnNode(Opcodes.IRETURN));
        list.add(new InsnNode(Opcodes.LRETURN));
        At at = new At() {
            @Override public int[] ordinal() { return new int[0]; }
            @Override public At.Location value() { return At.Location.TAIL; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return ""; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at);
        assertEquals(List.of(list.get(2)), nodes);
    }

    @Test
    public void testAtInvoke() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "Owner", "name", "(desc)", false));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "Other", "other", "(desc2)", false));
        list.add(new InsnNode(Opcodes.NOP));
        At at = new At() {
            @Override public int[] ordinal() { return new int[0]; }
            @Override public At.Location value() { return At.Location.INVOKE; }
            @Override public int opcode() { return Opcodes.INVOKEVIRTUAL; }
            @Override public String target() { return "Owner;name(desc)"; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at);
        assertEquals(List.of(list.get(1)), nodes);
    }

    @Test
    public void testAtField() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new FieldInsnNode(Opcodes.GETFIELD, "Owner", "name", "I"));
        list.add(new FieldInsnNode(Opcodes.PUTFIELD, "Other", "other", "J"));
        At at = new At() {
            @Override public int[] ordinal() { return new int[0]; }
            @Override public At.Location value() { return At.Location.FIELD; }
            @Override public int opcode() { return Opcodes.GETFIELD; }
            @Override public String target() { return "Owner;name"; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at);
        assertEquals(List.of(list.get(1)), nodes);
    }

    @Test
    public void testAtJump() {
        InsnList list = new InsnList();
        LabelNode label1 = new LabelNode();
        LabelNode label2 = new LabelNode();
        list.add(new JumpInsnNode(Opcodes.GOTO, label1));
        list.add(new JumpInsnNode(Opcodes.IFNULL, label2));
        list.add(new InsnNode(Opcodes.NOP));
        At at = new At() {
            @Override public int[] ordinal() { return new int[0]; }
            @Override public At.Location value() { return At.Location.JUMP; }
            @Override public int opcode() { return Opcodes.GOTO; }
            @Override public String target() { return ""; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at);
        assertEquals(List.of(list.get(0)), nodes);
    }

    @Test
    public void testAtReturnWithOrdinal() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new InsnNode(Opcodes.IRETURN));
        list.add(new InsnNode(Opcodes.LRETURN));
        list.add(new InsnNode(Opcodes.FRETURN));
        list.add(new InsnNode(Opcodes.DRETURN));
        list.add(new InsnNode(Opcodes.ARETURN));
        list.add(new InsnNode(Opcodes.RETURN));
        At at = new At() {
            @Override public int[] ordinal() { return new int[]{2, 4}; }
            @Override public At.Location value() { return At.Location.RETURN; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return ""; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at);
        assertEquals(List.of(list.get(3), list.get(5)), nodes);
    }
}
