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
import vi.mixin.api.util.TransformerHelper;
import vi.mixin.api.transformers.TransformerSupplier;

import java.util.List;

@SuppressWarnings("unused")
public class NewTransformer implements TransformerSupplier {

     private static boolean targetFilter(MethodNode mixinMethodNodeClone, MethodNode targetMethodNodeClone, New annotation, ClassNode origin) {
        if(annotation.value().isEmpty()) {
            return targetMethodNodeClone.name.equals("<init>") && targetMethodNodeClone.desc.split("\\)")[0].equals(mixinMethodNodeClone.desc.split("\\)")[0]);
        }
        return targetMethodNodeClone.name.equals("<init>") && targetMethodNodeClone.desc.equals("(" + annotation.value() + ")V");
    }

    private static void validate(AnnotatedMethodEditor mixinEditor, TargetMethodEditor targetEditor, ClassNode mixinClassNodeClone, ClassNode targetClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        String name = "@New " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if((mixinMethodNode.access & ACC_STATIC) == 0) throw new MixinFormatException(name, "should be static");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are: " + targetArgumentTypes[i] + (targetArgumentTypes[i].equals(Type.getType(Object.class)) ? "" : ", " + Type.getType(Object.class)));
        }
        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.getInternalName().equals(targetClassNodeClone.name) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + "L" + targetClassNodeClone.name + ";" + ", " + Type.getType(Object.class));
    }

    private static void extenderValidate(ExtenderAnnotatedMethodEditor mixinEditor, ExtenderTargetMethodEditor targetEditor, ClassNode mixinClassNodeClone) {
        MethodNode mixinMethodNode = mixinEditor.getMethodNodeClone();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        String name = "@New " + mixinClassNodeClone.name + "." + mixinMethodNode.name + mixinMethodNode.desc;
        if((mixinMethodNode.access & ACC_STATIC) == 0) throw new MixinFormatException(name, "should be static");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodNode.desc);
        Type[] targetArgumentTypes = Type.getArgumentTypes(targetMethodNode.desc);
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
                        if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && (!mixinArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE))
                throw new MixinFormatException(name, "valid types for argument number " + (i+1) + " are: " + targetArgumentTypes[i] + (targetArgumentTypes[i].equals(Type.getType(Object.class)) || targetArgumentTypes[i].getSort() <= Type.DOUBLE ? "" : ", " + Type.getType(Object.class)));
        }

        Type returnType = Type.getReturnType(mixinMethodNode.desc);
        if(!returnType.equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "valid return types are: void");
    }

    private static void mixinTransform(MixinAnnotatedMethodEditor mixinEditor, MixinTargetMethodEditor targetEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetOriginClassNodeClone) {
        validate(mixinEditor, targetEditor, mixinClassNodeClone, targetOriginClassNodeClone);
        mixinEditor.doNotCopyToTargetClass();
        MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        targetEditor.makePublic();

        InsnList insnList = new InsnList();
        insnList.add(new TypeInsnNode(NEW, targetOriginClassNodeClone.name));
        insnList.add(new InsnNode(DUP));

        TransformerHelper.addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(targetMethodNode.desc), true);

        insnList.add(new MethodInsnNode(INVOKESPECIAL, targetOriginClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
        insnList.add(new InsnNode(ARETURN));

        mixinEditor.setBytecode(insnList);
    }

    private static void accessorTransform(AccessorAnnotatedMethodEditor mixinEditor, AccessorTargetMethodEditor targetEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetOriginClassNodeClone) {
        validate(mixinEditor, targetEditor, mixinClassNodeClone, targetOriginClassNodeClone);
         MethodNode targetMethodNode = targetEditor.getMethodNodeClone();

        targetEditor.makePublic();

        InsnList insnList = new InsnList();
        insnList.add(new TypeInsnNode(NEW, targetOriginClassNodeClone.name));
        insnList.add(new InsnNode(DUP));

        TransformerHelper.addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(targetMethodNode.desc), true);

        insnList.add(new MethodInsnNode(INVOKESPECIAL, targetOriginClassNodeClone.name, targetMethodNode.name, targetMethodNode.desc));
        insnList.add(new InsnNode(ARETURN));

        mixinEditor.setBytecode(insnList);
    }

    private static void extenderTransform(ExtenderAnnotatedMethodEditor mixinEditor, ExtenderTargetMethodEditor targetEditor, New annotation, ClassNode mixinClassNodeClone, ClassNode targetOriginClassNodeClone) {
        extenderValidate(mixinEditor, targetEditor, mixinClassNodeClone);
        targetEditor.makePublic();
        mixinEditor.delete();
    }

    @Override
    public List<BuiltTransformer> getBuiltTransformers() {
        return List.of(
                TransformerBuilder.getTransformerBuilder(MixinMixinClassType.class).annotation(New.class).annotatedMethod().targetMethod().transformFunction(NewTransformer::mixinTransform).targetFilter(NewTransformer::targetFilter).build(),
                TransformerBuilder.getTransformerBuilder(AccessorMixinClassType.class).annotation(New.class).annotatedMethod().targetMethod().transformFunction(NewTransformer::accessorTransform).targetFilter(NewTransformer::targetFilter).build(),
                TransformerBuilder.getTransformerBuilder(ExtenderMixinClassType.class).annotation(New.class).annotatedMethod().targetMethod().transformFunction(NewTransformer::extenderTransform).targetFilter(NewTransformer::targetFilter).build()
        );
    }
}
