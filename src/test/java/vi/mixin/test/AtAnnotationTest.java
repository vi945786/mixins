package vi.mixin.test;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.injection.At;
import vi.mixin.api.util.TransformerHelper;

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
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.HEAD; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return ""; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at, "");
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
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.RETURN; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return ""; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at, "");
        assertEquals(List.of(list.get(1), list.get(2)), nodes);
    }

    @Test
    public void testAtTail() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new InsnNode(Opcodes.IRETURN));
        list.add(new InsnNode(Opcodes.LRETURN));
        At at = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.TAIL; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return ""; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at, "");
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
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.INVOKE; }
            @Override public int opcode() { return Opcodes.INVOKEVIRTUAL; }
            @Override public String target() { return "Owner.name(desc)"; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at, "");
        assertEquals(List.of(list.get(1)), nodes);
    }

    @Test
    public void testAtField() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new FieldInsnNode(Opcodes.GETFIELD, "Owner", "name", "I"));
        list.add(new FieldInsnNode(Opcodes.PUTFIELD, "Other", "other", "J"));
        At at = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.FIELD; }
            @Override public int opcode() { return Opcodes.GETFIELD; }
            @Override public String target() { return "Owner.name"; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at, "");
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
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.JUMP; }
            @Override public int opcode() { return Opcodes.GOTO; }
            @Override public String target() { return ""; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at, "");
        assertEquals(List.of(list.get(0)), nodes);
    }

    @Test
    public void testAtReturnWithOrdinals() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new InsnNode(Opcodes.IRETURN));
        list.add(new InsnNode(Opcodes.LRETURN));
        list.add(new InsnNode(Opcodes.FRETURN));
        list.add(new InsnNode(Opcodes.DRETURN));
        list.add(new InsnNode(Opcodes.ARETURN));
        list.add(new InsnNode(Opcodes.RETURN));
        At at = new At() {
            @Override public int[] ordinals() { return new int[]{2, 4}; }
            @Override public At.Location value() { return At.Location.RETURN; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return ""; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at, "");
        assertEquals(List.of(list.get(3), list.get(5)), nodes);
    }

    @Test
    public void testAtStore() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new VarInsnNode(Opcodes.ISTORE, 1));
        list.add(new VarInsnNode(Opcodes.LSTORE, 2));
        list.add(new VarInsnNode(Opcodes.FSTORE, 3));
        list.add(new VarInsnNode(Opcodes.DSTORE, 4));
        list.add(new VarInsnNode(Opcodes.ASTORE, 5));
        list.add(new VarInsnNode(Opcodes.ISTORE, 6));
        At at = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.STORE; }
            @Override public int opcode() { return Opcodes.ISTORE; }
            @Override public String target() { return ""; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at, "");
        assertEquals(List.of(list.get(1), list.get(6)), nodes);
    }

    @Test
    public void testAtLoad() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new VarInsnNode(Opcodes.ILOAD, 1));
        list.add(new VarInsnNode(Opcodes.LLOAD, 2));
        list.add(new VarInsnNode(Opcodes.ALOAD, 3));
        list.add(new VarInsnNode(Opcodes.ILOAD, 4));
        At at = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.LOAD; }
            @Override public int opcode() { return Opcodes.ILOAD; }
            @Override public String target() { return ""; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, at, "");
        assertEquals(List.of(list.get(1), list.get(4)), nodes);
    }

    @Test
    public void testAtConstant() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new InsnNode(Opcodes.ICONST_3));
        list.add(new IntInsnNode(Opcodes.SIPUSH, 123));
        list.add(new LdcInsnNode(123));
        list.add(new LdcInsnNode("hi"));
        list.add(new LdcInsnNode(Type.getType(Object.class)));
        list.add(new InsnNode(Opcodes.ACONST_NULL));

        At atInt3 = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.CONSTANT; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return "int;3"; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodesInt3 = TransformerHelper.getAtTargetNodes(list, atInt3, "");
        assertEquals(List.of(list.get(1)), nodesInt3);

        At atInt123 = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.CONSTANT; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return "int;123"; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodesInt123 = TransformerHelper.getAtTargetNodes(list, atInt123, "");
        assertEquals(List.of(list.get(2), list.get(3)), nodesInt123);

        At atString = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.CONSTANT; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return "string;hi"; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodesString = TransformerHelper.getAtTargetNodes(list, atString, "");
        assertEquals(List.of(list.get(4)), nodesString);

        At atNull = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.CONSTANT; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return "null"; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodesNull = TransformerHelper.getAtTargetNodes(list, atNull, "");
        assertEquals(List.of(list.get(6)), nodesNull);

        At atClass = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.CONSTANT; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return "class;Ljava/lang/Object;"; }
            @Override public int offset() { return 0; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodesClass = TransformerHelper.getAtTargetNodes(list, atClass, "");
        assertEquals(List.of(list.get(5)), nodesClass);
    }

    @Test
    public void testAtOffset() {
        InsnList list = new InsnList();
        list.add(new InsnNode(Opcodes.NOP));
        list.add(new InsnNode(Opcodes.IRETURN));
        list.add(new InsnNode(Opcodes.LRETURN));
        list.add(new InsnNode(Opcodes.NOP));
        At atOffset = new At() {
            @Override public int[] ordinals() { return new int[0]; }
            @Override public At.Location value() { return At.Location.RETURN; }
            @Override public int opcode() { return -1; }
            @Override public String target() { return ""; }
            @Override public int offset() { return 1; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return At.class; }
        };
        List<AbstractInsnNode> nodes = TransformerHelper.getAtTargetNodes(list, atOffset, "");
        assertEquals(List.of(list.get(2), list.get(3)), nodes);
    }
}
