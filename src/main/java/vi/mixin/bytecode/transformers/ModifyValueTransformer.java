package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.ModifyValue;
import vi.mixin.api.classtypes.mixintype.MixinAnnotatedMethodEditor;
import vi.mixin.api.classtypes.mixintype.MixinMixinClassType;
import vi.mixin.api.classtypes.mixintype.MixinTargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetInsnListEditor;
import vi.mixin.api.injection.Vars;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.transformers.TransformerBuilder;
import vi.mixin.api.transformers.TransformerSupplier;
import vi.mixin.api.util.TransformerHelper;
import vi.mixin.api.util.TypeAwareBasicInterpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class ModifyValueTransformer implements TransformerSupplier {
    private Type[] args = null;

    private void setArg(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, ModifyValue annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        String name = "@ModifyValue " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;

        Analyzer<BasicValue> analyzer = new Analyzer<>(new TypeAwareBasicInterpreter());
        try {
            analyzer.analyze(targetClassNodeClone.name, targetMethodNode);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }

        Type type = null;
        Frame<BasicValue>[] frames = analyzer.getFrames();
        for(int atIndex : TransformerHelper.getAtTargetIndexes(targetMethodNode.instructions, annotation.at(), name)) {
            Frame<BasicValue> frame = frames[atIndex];
            Type t = frame.getStack(frame.getStackSize() -1).getType();

            if(type == null) type = t;
            else if(!type.equals(t)) {
                type = Type.getType(Object.class);
                break;
            }
        }

        if(!mixinMethodNode.desc.startsWith("()") && List.of(Type.getArgumentTypes(mixinMethodNode.desc)).getLast().equals(Type.getType(Vars.class))) args = new Type[] {type, Type.getType(Vars.class)};
        else args = new Type[] {type};
    }

    private void validate(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, ClassNode mixinClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        String name = "@ModifyValue " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if((targetMethodNode.access & ACC_STATIC) != (mixinMethodNode.access & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((targetMethodNode.access & ACC_STATIC) != 0 ? "" : "not") + " static");
        if(!Type.getReturnType(mixinMethodNode.desc).equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + Type.getType(Object.class));
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = args;
        if(mixinArgumentTypes.length < targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && (!mixinArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE))
                throw new MixinFormatException(name, "valid types for argument number " + (i+1) + " are: " + targetArgumentTypes[i] + (targetArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE ? "" : ", " + Type.getType(Object.class)));
        }
    }

    private void transform(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, ModifyValue annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        setArg(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        validate(mixinEditor, targetEditor, mixinClassNodeClone);
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        mixinEditor.makePublic();

        MixinClassTargetInsnListEditor insnListEditor = targetEditor.getInsnListEditor();
        InsnList targetList = insnListEditor.getInsnListClone();
        List<Integer> atIndexes = TransformerHelper.getAtTargetIndexesThrows(targetList, annotation.at(), "@ModifyValue " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc);

        boolean isStatic = (mixinMethodNode.access & ACC_STATIC) != 0;

        Map<Integer, Type> types = new HashMap<>();
        Type[] mixinArgs = Type.getArgumentTypes(mixinMethodNode.desc);

        Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
        try {
            analyzer.analyze(targetClassNodeClone.name, targetMethodNode);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }

        for (int atIndex : atIndexes) {
            AbstractInsnNode cursor = targetList.get(atIndex).getPrevious();
            int remaining = mixinArgs.length;
            List<AbstractInsnNode> argLoads = new ArrayList<>();
            Frame<BasicValue> f = analyzer.getFrames()[atIndex];
            types.put(atIndex, f.getStack(f.getStackSize() -1).getType());

            while (remaining > 0 && cursor != null) {
                Frame<BasicValue> cf = analyzer.getFrames()[targetList.indexOf(cursor) + 1];
                if (cf != null && cf.getStackSize() == f.getStackSize() - remaining + 1) {
                    argLoads.addFirst(cursor);
                    remaining -= cf.getStack(cf.getStackSize() - 1).getSize();
                }
                cursor = cursor.getPrevious();
            }

            if(!isStatic) insnListEditor.insertBefore(argLoads.isEmpty() ? atIndex : targetList.indexOf(argLoads.getFirst()), new VarInsnNode(ALOAD, 0));
        }

        boolean hasVars = args.length == 2;
        boolean isArgObject = args[0].equals(Type.getType(Object.class));
        for (int atIndex : atIndexes) {
            Type type = types.get(atIndex);
            //box
            if(isArgObject && type.getSort() <= Type.DOUBLE)  {
                Type boxed = TransformerHelper.getBoxedType(type);
                insnListEditor.insertBefore(atIndex, new MethodInsnNode(INVOKESTATIC, boxed.getInternalName(), "valueOf", "(" + type.getDescriptor() + ")" + boxed.getDescriptor()));
            }
            if(hasVars) insnListEditor.insertBefore(atIndex, targetEditor.getCaptureLocalsInsnList(atIndex, targetClassNodeClone.name));
            insnListEditor.insertBefore(atIndex, new MethodInsnNode(INVOKESTATIC, mixinClassNodeClone.name, mixinMethodNode.name, mixinEditor.getUpdatedDesc(targetClassNodeClone.name)));
            //unbox
            if(type.getSort() <= Type.DOUBLE)  {
                Type boxed = TransformerHelper.getBoxedType(type);
                insnListEditor.insertBefore(atIndex, new MethodInsnNode(INVOKEVIRTUAL, boxed.getInternalName(), type.getClassName() + "Value", "()" + type.getDescriptor()));
            }
        }
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.getTransformerBuilder(MixinMixinClassType.class).annotation(ModifyValue.class).annotatedMethod().targetMethod().transformFunction(
                        (MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, ModifyValue annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) ->
                                new ModifyValueTransformer().transform(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone)
                ).build()
        );
    }
}
