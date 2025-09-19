package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.classtypes.accessortype.AccessorAnnotatedMethodEditor;
import vi.mixin.api.classtypes.accessortype.AccessorMixinClassType;
import vi.mixin.api.classtypes.accessortype.AccessorTargetMethodEditor;
import vi.mixin.api.classtypes.extendertype.ExtenderAnnotatedMethodEditor;
import vi.mixin.api.classtypes.extendertype.ExtenderMixinClassType;
import vi.mixin.api.classtypes.extendertype.ExtenderTargetMethodEditor;
import vi.mixin.api.classtypes.mixintype.MixinAnnotatedMethodEditor;
import vi.mixin.api.classtypes.mixintype.MixinMixinClassType;
import vi.mixin.api.classtypes.mixintype.MixinTargetMethodEditor;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.transformers.BuiltTransformer;
import vi.mixin.api.transformers.TransformerBuilder;
import vi.mixin.api.transformers.TransformerHelper;
import vi.mixin.api.transformers.TransformerSupplier;

import java.util.List;

public class NewTransformer implements TransformerSupplier {

     private static boolean targetFilter(MethodNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, New annotation) {
        if(annotation.value().isEmpty()) {
            return targetMethodNodeClone.name.equals("<init>") && targetMethodNodeClone.desc.split("\\)")[0].equals(mixinMethodNodeClone.desc.split("\\)")[0]);
        }
        return targetMethodNodeClone.name.equals("<init>") && targetMethodNodeClone.desc.equals("(" + annotation.value() + ")V");
    }

    private static void validate(AnnotatedMethodEditor mixinEditor, TargetMethodEditor targetEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();

        String name = "@New " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if(targetEditor.getNumberOfTargets() != 1) throw new MixinFormatException(name, "illegal number of targets, should be 1");
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone(0);

        if((mixinMethodNode.access & ACC_STATIC) == 0) throw new MixinFormatException(name, "should be static");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.getInternalName().equals(targetClassNodeClone.name) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + "L" + targetClassNodeClone.name + ";" + ", " + Type.getType(Object.class));
    }

    private static void extenderValidate(ExtenderAnnotatedMethodEditor mixinEditor, ExtenderTargetMethodEditor targetEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();

        String name = "@New " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if(targetEditor.getNumberOfTargets() != 1) throw new MixinFormatException(name, "illegal number of targets, should be 1");
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone(0);

        if((mixinMethodNode.access & ACC_STATIC) == 0) throw new MixinFormatException(name, "should be static");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }

        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "valid return types are: void");
    }

    private static void mixinTransform(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        mixinEditor.doNotCopyToTargetClass();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone(0);

        targetEditor.makePublic(0);

        InsnList insnList = new InsnList();
        insnList.add(new TypeInsnNode(NEW, targetClassNodeClone.name));
        insnList.add(new InsnNode(DUP));

        TransformerHelper.addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(targetMethodNode.desc), true);

        insnList.add(new MethodInsnNode(INVOKESPECIAL, targetClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
        insnList.add(new InsnNode(ARETURN));

        mixinEditor.setBytecode(insnList);
    }

    private static void accessorTransform(AccessorAnnotatedMethodEditor mixinEditor, AccessorTargetMethodEditor targetEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        validate(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
         MethodNode targetMethodNode = targetEditor.getMethodNodeClone(0);

        targetEditor.makePublic(0);

        InsnList insnList = new InsnList();
        insnList.add(new TypeInsnNode(NEW, targetClassNodeClone.name));
        insnList.add(new InsnNode(DUP));

        TransformerHelper.addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(targetMethodNode.desc), true);

        insnList.add(new MethodInsnNode(INVOKESPECIAL, targetClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
        insnList.add(new InsnNode(ARETURN));

        mixinEditor.setBytecode(insnList);
    }

    private static void extenderTransform(ExtenderAnnotatedMethodEditor mixinEditor, ExtenderTargetMethodEditor targetEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        extenderValidate(mixinEditor, targetEditor, annotation, mixinClassNodeClone, targetClassNodeClone);
        targetEditor.makePublic(0);
        mixinEditor.delete();
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.annotatedMethodTransformerBuilder(MixinMixinClassType.class, New.class).withMethodTarget().setTargetFilter(NewTransformer::targetFilter).setTransformer(NewTransformer::mixinTransform).build(),
                TransformerBuilder.annotatedMethodTransformerBuilder(AccessorMixinClassType.class, New.class).withMethodTarget().setTargetFilter(NewTransformer::targetFilter).setTransformer(NewTransformer::accessorTransform).build(),
                TransformerBuilder.annotatedMethodTransformerBuilder(ExtenderMixinClassType.class, New.class).withMethodTarget().setTargetFilter(NewTransformer::targetFilter).setTransformer(NewTransformer::extenderTransform).build()
        );
    }
}
