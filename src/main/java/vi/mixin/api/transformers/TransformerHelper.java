package vi.mixin.api.transformers;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.injection.At;
import vi.mixin.bytecode.MixinClassHelper;
import vi.mixin.bytecode.Mixiner;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.objectweb.asm.tree.AbstractInsnNode.*;

public final class TransformerHelper implements Opcodes {

    public static Type getBoxedType(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN: return Type.getType(Boolean.class);
            case Type.CHAR:    return Type.getType(Character.class);
            case Type.BYTE:    return Type.getType(Byte.class);
            case Type.SHORT:   return Type.getType(Short.class);
            case Type.INT:     return Type.getType(Integer.class);
            case Type.FLOAT:   return Type.getType(Float.class);
            case Type.LONG:    return Type.getType(Long.class);
            case Type.DOUBLE:  return Type.getType(Double.class);
            default: throw new IllegalArgumentException("Not a primitive: " + type);
        }
    }

    public static int getReturnOpcode(Type returnType) {
        return switch (returnType.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> IRETURN;
            case Type.FLOAT -> FRETURN;
            case Type.LONG -> LRETURN;
            case Type.DOUBLE -> DRETURN;
            case Type.VOID -> RETURN;
            default -> ARETURN;
        };
    }

    public static int getLoadOpcode(Type argument) {
        return switch (argument.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.INT -> ILOAD;
            case Type.FLOAT -> FLOAD;
            case Type.LONG -> LLOAD;
            case Type.DOUBLE -> DLOAD;
            default -> ALOAD;
        };
    }

    public static int[] getLoadOpcodes(Type[] arguments) {
        return Arrays.stream(arguments).mapToInt(TransformerHelper::getLoadOpcode).toArray();
    }

    public static void addLoadOpcodesOfMethod(InsnList list, Type[] arguments, boolean isStatic) {
        int[] loadOpcodes = TransformerHelper.getLoadOpcodes(arguments);

        if(!isStatic) list.add(new VarInsnNode(ALOAD, 0));
        for (int i = 0; i < loadOpcodes.length; i++) {
            list.add(new VarInsnNode(loadOpcodes[i], i + (isStatic ? 0 : 1)));
        }
    }

    public static boolean doMethodDescsMatch(String search, String target) {
        Type[] searchArgumentTypes = Type.getArgumentTypes(search);
        Type[] targetArgumentTypes = Type.getArgumentTypes(target);
        if(searchArgumentTypes.length != targetArgumentTypes.length) return false;
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!searchArgumentTypes[i].equals(targetArgumentTypes[i]) && !searchArgumentTypes[i].equals(Type.getType(Object.class))) return false;
        }

        if (!Type.getReturnType(search).equals(Type.getReturnType(target)) && !Type.getReturnType(search).equals(Type.getType(Object.class))) return false;

        return true;
    }

    public static MethodNode getTargetMethod(ClassNode classNode, String nameAndDesc) {
        String name = nameAndDesc.split("\\(")[0];
        String desc = "(" + nameAndDesc.split("\\(")[1];

        return classNode.methods.stream().filter(methodNode -> {
            if(!methodNode.name.equals(name)) return false;

            return doMethodDescsMatch(desc, methodNode.desc);
        }).findAny().orElse(null);
    }

    public static Class<?> getTargetClass(ClassNode mixinNode) {
        return Mixiner.getTargetClass(mixinNode);
    }

    public static List<AbstractInsnNode> getAtTargetNodesThrows(InsnList list, At atAnnotation, String annotatedName) {
        return getAtTargetIndexesThrows(list, atAnnotation, annotatedName).stream().map(list::get).toList();
    }

    public static List<AbstractInsnNode> getAtTargetNodes(InsnList list, At atAnnotation, String annotatedName) {
        return getAtTargetIndexes(list, atAnnotation, annotatedName).stream().map(list::get).toList();
    }

    public static List<Integer> getAtTargetIndexesThrows(InsnList list, At atAnnotation, String annotatedName) {
        List<Integer> indexes = getAtTargetIndexes(list, atAnnotation, annotatedName);
        if(indexes.isEmpty()) throw new MixinFormatException(annotatedName, "@At has no targets");
        return indexes;
    }

    public static List<Integer> getAtTargetIndexes(InsnList list, At atAnnotation, String annotatedName) {
         At.Location location = atAnnotation.value();
         List<Integer> ordinal = Arrays.stream(atAnnotation.ordinal()).boxed().toList();
         String target = atAnnotation.target();
         int opcode = atAnnotation.opcode();

         return switch (location) {
             case HEAD -> {
                 if(!target.isEmpty() || opcode != -1)
                     throw new MixinFormatException(annotatedName, "unsupported head target or opcode");

                 yield List.of(0);
             }
             case RETURN -> {
                 if (!target.isEmpty() || opcode != -1)
                     throw new MixinFormatException(annotatedName, "unsupported return target or opcode");

                 yield getInsnNodesIndexes(list, INSN, List.of(IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN), ordinal);
             }
             case TAIL -> {
                 if (!target.isEmpty() || opcode != -1)
                     throw new MixinFormatException(annotatedName, "unsupported tail target or opcode");

                 yield List.of(getInsnNodesIndexes(list, INSN, List.of(IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN), List.of()).getLast());
             }
             case INVOKE -> {
                 if(opcode != -1 && !(INVOKEVIRTUAL <= opcode && opcode <= INVOKEINTERFACE))
                     throw new MixinFormatException(annotatedName, "unsupported method opcode. supported values are: Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE");

                 int splitOwner = target.indexOf('.');
                 int splitName = target.indexOf('(');
                 String owner = target.substring(0, splitOwner);
                 String name = target.substring(splitOwner+1, splitName);
                 String desc = target.substring(splitName);
                 yield getInsnNodesIndexes(list, METHOD_INSN, List.of(INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE), ordinal, owner, name, desc);
             }
             case FIELD -> {
                 if(opcode != -1 && !(GETSTATIC <= opcode && opcode <= PUTFIELD))
                     throw new MixinFormatException("@At", "unsupported field opcode. supported values are: Opcodes.GETSTATIC, Opcodes.PUTSTATIC, Opcodes.GETFIELD, Opcodes.PUTFIELD");

                 int splitOwner = target.indexOf('.');
                 String owner = target.substring(0, splitOwner);
                 String name = target.substring(splitOwner+1).split(";")[0];
                 yield getInsnNodesIndexes(list, FIELD_INSN, opcode, ordinal, owner, name, null);
             }
             case JUMP -> {
                 if (!target.isEmpty())
                     throw new MixinFormatException("@At", "unsupported jump target");
                 if(opcode != -1 && (!(IFEQ <= opcode && opcode <= JSR)) && !(IFNULL <= opcode && opcode <= IFNONNULL))
                     throw new MixinFormatException("@At", "unsupported jump opcode. supported values are: Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE, Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.GOTO, Opcodes.JSR, Opcodes.IFNULL, Opcodes.IFNONNULL");

                 yield getInsnNodesIndexes(list, JUMP_INSN, opcode, ordinal);
             }
         };
    }

    public static List<Integer> getInsnNodesIndexes(InsnList list, int type, Integer opcode, Object... values) {
        return getInsnNodesIndexes(list, type, opcode == null ? null : List.of(opcode), List.of(), values);
    }

    public static List<Integer> getInsnNodesIndexes(InsnList list, int type, Integer opcode, List<Integer> ordinals, Object... values) {
        return getInsnNodesIndexes(list, type, opcode == null ? null : List.of(opcode), ordinals, values);
    }

    public static List<Integer> getInsnNodesIndexes(InsnList list, int type, List<Integer> opcodes, List<Integer> ordinals, Object... values) {
        List<Integer> indexes = new ArrayList<>();
        for(int i = 0; i < list.size(); i++) {
            AbstractInsnNode insnNode = list.get(i);
            if((insnNode.getType() != type && type != -1) || (opcodes != null && !opcodes.contains(insnNode.getOpcode()))) continue;

            if(equals(insnNode, values)) indexes.add(i);
        }

        if(ordinals == null || ordinals.isEmpty()) return indexes;
        ordinals = ordinals.stream().sorted().toList();
        List<Integer> newIndexes = new ArrayList<>();
        for (int i = 0; i < indexes.size(); i++) {
            if(ordinals.contains(i)) newIndexes.add(indexes.get(i));
        }
        if(ordinals.getLast() >= indexes.size()) {
            throw new IndexOutOfBoundsException("ordinal is too big");
        }
        if(ordinals.getFirst() < 0) {
            throw new IndexOutOfBoundsException("ordinal is negative");
        }

        return newIndexes;
    }

    public static List<AbstractInsnNode> getInsnNodes(InsnList list, int type, Integer opcode, Object... values) {
        return getInsnNodes(list, type, opcode == null ? null : List.of(opcode), List.of(), values);
    }

    public static List<AbstractInsnNode> getInsnNodes(InsnList list, int type, Integer opcode, List<Integer> ordinals, Object... values) {
        return getInsnNodes(list, type, opcode == null ? null : List.of(opcode), ordinals, values);
    }

    public static List<AbstractInsnNode> getInsnNodes(InsnList list, int type, List<Integer> opcodes, List<Integer> ordinals, Object... values) {
        return getInsnNodesIndexes(list, type, opcodes, ordinals, values).stream().map(list::get).toList();
    }

    public static boolean equals(AbstractInsnNode node, Object... values) {
        return switch (node) {
            case null -> false;
            case InsnNode n -> true;
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

    public static String getOuterClassInstanceFieldName(ClassNode classNode, Class<?> target) {
        FieldNode fieldNode = classNode.fields.stream().filter(f -> f.name.startsWith("this$") && (f.access & ACC_SYNTHETIC) != 0).findFirst().orElse(null);
        if(fieldNode != null) return fieldNode.name;

        Class<?> c = target;

        int i = 0;
        while(c != null) {
            if((c.getModifiers() & ACC_STATIC) != 0) break;
            c = c.getDeclaringClass();
            i++;
        }
        if(i == 0) return null;
        int finalI = i;
        var ref = new Object() {
            String name = "this$" + (finalI -1);
        };

        while(classNode.fields.stream().anyMatch(f -> f.name.equals(ref.name))) ref.name += "$";

        return ref.name;
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

     public static <T extends Annotation> T getAnnotation(AnnotationNode node) {
        Class<?> annotationClass = MixinClassHelper.findClass(node.desc.substring(1, node.desc.length() - 1));

         Map<String, Object> values = new HashMap<>();
        if (node.values != null) {
            for (int i = 0; i < node.values.size(); i += 2) {
                String name = (String) node.values.get(i);
                Object value = node.values.get(i + 1);
                values.put(name, convertValue(value));
            }
        }

        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("annotationType")) return annotationClass;
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);

            if (values.containsKey(method.getName())) {
                return values.get(method.getName());
            }
            return method.getDefaultValue();
        };

        return (T) Proxy.newProxyInstance(
                annotationClass.getClassLoader(),
                new Class[]{annotationClass},
                handler
        );
    }

    private static Object convertValue(Object value) {
        if (value instanceof List list) {
            Object[] arr = new Object[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = convertValue(list.get(i));
            }
            return arr;
        } else if (value instanceof Type type) {
            return MixinClassHelper.findClass(type.getClassName());
        } else if (value instanceof AnnotationNode nested) {
            return getAnnotation(nested);
        } else if (value instanceof String[] enumDesc && enumDesc.length == 2) {
            String className = enumDesc[0].substring(1, enumDesc[0].length() - 1).replace('/', '.');
            Class<?> enumClass = MixinClassHelper.findClass(className);
            return Enum.valueOf((Class<Enum>) enumClass, enumDesc[1]);
        }
        return value;
    }
}
