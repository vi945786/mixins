package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.Redirect;
import vi.mixin.api.classtypes.mixintype.MixinAnnotatedMethodEditor;
import vi.mixin.api.classtypes.mixintype.MixinMixinClassType;
import vi.mixin.api.classtypes.mixintype.MixinTargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.TargetInsnListManipulator;
import vi.mixin.api.injection.At;
import vi.mixin.api.injection.Vars;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.transformers.TransformerBuilder;
import vi.mixin.api.util.TransformerHelper;
import vi.mixin.api.transformers.TransformerSupplier;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class RedirectTransformer implements TransformerSupplier {

    private static Type[] getArgs(MixinAnnotatedMethodEditor mixinEditor, Redirect annotation, ClassNode mixinClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();

        String name = "@Redirect " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;

        List<Type> args = new ArrayList<>();

        At at = annotation.at();
        if(at.offset() != 0) throw new MixinFormatException(name, "unsupported @Redirect @At offset.");
        switch (at.value()) {
            case INVOKE -> {
               switch (at.opcode()) {
                   case INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE  -> args.add(Type.getType("L" + at.target().split("\\.")[0] + ";"));
                   case INVOKESTATIC -> {}
                   default -> throw new MixinFormatException(name, "unsupported @At method opcode. supported values are: Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE");
               }
               args.addAll(List.of(Type.getArgumentTypes("(" + at.target().split("\\(")[1])));
            }
            case FIELD -> {
                switch (at.opcode()) {
                    case GETSTATIC -> {}
                    case PUTSTATIC -> args.add(Type.getType(at.target().split(":")[1]));
                    case GETFIELD -> args.add(Type.getType("L" + at.target().split("\\.")[0] + ";"));
                    case PUTFIELD -> {
                        args.add(Type.getType("L" + at.target().split("\\.")[0] + ";"));
                        args.add(Type.getType(at.target().split(":")[1]));
                    }
                    default -> throw new MixinFormatException(name, "unsupported @At field opcode. supported values are: Opcodes.GETSTATIC, Opcodes.PUTSTATIC, Opcodes.GETFIELD, Opcodes.PUTFIELD");
               }
            }
            default -> throw new MixinFormatException(name, "unsupported @At location. supported values are: INVOKE, FIELD");
        }

        if(!mixinMethodNode.desc.startsWith("()") && List.of(Type.getArgumentTypes(mixinMethodNode.desc)).getLast().equals(Type.getType(Vars.class))) args.add(Type.getType(Vars.class));
        return args.toArray(Type[]::new);
    }

    private static Type getReturnType(MixinAnnotatedMethodEditor mixinEditor, Redirect annotation, ClassNode mixinClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();

        String name = "@Redirect " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;

        At at = annotation.at();
        return switch (at.value()) {
            case INVOKE -> Type.getReturnType("(" + at.target().split("\\(")[1]);
            case FIELD -> switch (at.opcode()) {
                case GETSTATIC, GETFIELD -> Type.getType(at.target().split(":")[1]);
                case PUTSTATIC, PUTFIELD -> Type.VOID_TYPE;
                default -> throw new MixinFormatException(name, "unsupported @At method opcode. supported values are: Opcodes.GETSTATIC, Opcodes.PUTSTATIC, Opcodes.GETFIELD, Opcodes.PUTFIELD");
           };
            default -> throw new MixinFormatException(name, "unsupported @At location. supported values are: INVOKE, FIELD");
        };
    }

    private static void validate(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, Redirect annotation, ClassNode mixinClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        String name = "@Redirect " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if((targetMethodNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((targetMethodNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        if(!Type.getReturnType(mixinMethodNode.desc).equals(getReturnType(mixinEditor, annotation, mixinClassNodeClone)) && !Type.getReturnType(mixinMethodNode.desc).equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + Type.getReturnType(targetMethodNode.desc) + ", " + Type.getType(Object.class));
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = getArgs(mixinEditor, annotation, mixinClassNodeClone);
        if(mixinArgumentTypes.length < targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
                        if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && (!mixinArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE))
                throw new MixinFormatException(name, "valid types for argument number " + (i+1) + " are: " + targetArgumentTypes[i] + (targetArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE ? "" : ", " + Type.getType(Object.class)));
        }
    }

    private static void transform(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, Redirect annotation, ClassNode mixinClassNodeClone, ClassNode targetOriginClassNodeClone) {
        validate(mixinEditor, targetEditor, annotation, mixinClassNodeClone);
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        mixinEditor.makePublic();

        TargetInsnListManipulator insnListEditor = targetEditor.getInsnListEditor();
        InsnList targetList = insnListEditor.getInsnListClone();
        List<Integer> atIndexes = TransformerHelper.getAtTargetIndexesThrows(targetList, annotation.at(), "@Redirect " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc);

        boolean isStatic = (mixinMethodNode.access & ACC_STATIC) != 0;

        if(!isStatic) {
            Type[] mixinArgs = Type.getArgumentTypes(mixinMethodNode.desc);

            Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
            try {
                analyzer.analyze(targetOriginClassNodeClone.name, targetMethodNode);
            } catch (AnalyzerException e) {
                throw new RuntimeException(e);
            }

            for (int atIndex : atIndexes) {
                AbstractInsnNode cursor = targetList.get(atIndex).getPrevious();
                int remaining = mixinArgs.length;
                List<AbstractInsnNode> argLoads = new ArrayList<>();
                Frame<BasicValue> f = analyzer.getFrames()[atIndex];

                while (remaining > 0 && cursor != null) {
                    Frame<BasicValue> cf = analyzer.getFrames()[targetList.indexOf(cursor) + 1];
                    if (cf != null && cf.getStackSize() == f.getStackSize() - remaining + 1) {
                        argLoads.addFirst(cursor);
                        remaining -= cf.getStack(cf.getStackSize() - 1).getSize();
                    }
                    cursor = cursor.getPrevious();
                }

                insnListEditor.insertBefore(argLoads.isEmpty() ? atIndex : targetList.indexOf(argLoads.getFirst()), new VarInsnNode(ALOAD, 0));
            }
        }

        boolean hasVars = !mixinMethodNode.desc.startsWith("()") && List.of(Type.getArgumentTypes(mixinMethodNode.desc)).getLast().equals(Type.getType(Vars.class));
        for (int atIndex : atIndexes) {
            if(hasVars) insnListEditor.insertBefore(atIndex, targetEditor.getCaptureLocalsInsnList(atIndex, targetOriginClassNodeClone.name));
            insnListEditor.insertBefore(atIndex, new MethodInsnNode(INVOKESTATIC, mixinClassNodeClone.name, mixinMethodNode.name, mixinEditor.getUpdatedDesc()));
            insnListEditor.remove(atIndex);
        }
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.getTransformerBuilder(MixinMixinClassType.class).annotation(Redirect.class).annotatedMethod().targetMethod().transformFunction(RedirectTransformer::transform).build()
        );
    }
}
