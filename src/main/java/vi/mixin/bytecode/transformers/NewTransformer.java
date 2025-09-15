package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.editors.BytecodeEditor;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.MethodTransformer;
import vi.mixin.api.transformers.TransformerHelper;

import java.util.ArrayList;
import java.util.List;

import static vi.mixin.api.transformers.TransformerHelper.getMethodEditor;

public class NewTransformer implements MethodTransformer<New> {

    private static void validate(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, New mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@New " + mixinClassEditor.getName() + "." + mixinMethodEditor.getName() + mixinMethodEditor.getDesc();
        MethodEditor target = targetClassEditor.getMethodEditor("<init>(" + mixinAnnotation.value() + ")V");
        if(target == null) throw new MixinFormatException(name, "target doesn't exist");
        if((mixinMethodEditor.getAccess() & ACC_STATIC) == 0) throw new MixinFormatException(name, "should be static");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodEditor.getDesc());
        Type[] targetArgumentTypes = Type.getArgumentTypes(target.getDesc());
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
        if((mixinClassEditor.getAccess() & ACC_INTERFACE) != 0 && targetClassEditor.getMethodEditors().stream().anyMatch(method -> method.getName().equals(mixinMethodEditor.getName())
                && method.getDesc().split("\\)")[0].equals(mixinMethodEditor.getDesc().split("\\)")[0]))) throw new MixinFormatException(name, "method with this name and desc already exists in the target class.");
        if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) != null) {
            Type returnType = Type.getReturnType(mixinMethodEditor.getDesc());
            if(!returnType.equals(Type.VOID_TYPE)) throw new MixinFormatException(name, "valid return types are: void");
            for (ClassEditor classEditor : mixinClassEditor.getAllClassesInHierarchy()) {
                for (MethodEditor methodEditor : classEditor.getMethodEditors()) {
                    if (methodEditor.getName().equals("<init>")) continue;
                    for (AbstractInsnNode node : methodEditor.getBytecodeEditor().getBytecode()) {
                        if (!(node instanceof MethodInsnNode methodInsnNode)) continue;
                        MethodEditor nodeMethodEditor = getMethodEditor(mixinClassEditor, methodInsnNode);
                        if (nodeMethodEditor != null && nodeMethodEditor.getAnnotationEditors(Type.getDescriptor(New.class)) != null) throw new MixinFormatException(name, "@New calls in @Extends classes are only allowed in constructors");
                    }
                }
            }
        } else {
            Type returnType = Type.getReturnType(mixinMethodEditor.getDesc());
            if(!returnType.getInternalName().equals(targetClassEditor.getName()) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + "L" + targetClassEditor.getName() + ";" + ", " + Type.getType(Object.class));
        }
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, New mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinMethodEditor, mixinAnnotation, targetClassEditor);
        MethodEditor targetMethodEditor = targetClassEditor.getMethodEditor("<init>(" + mixinAnnotation.value() + ")V");

        targetMethodEditor.makePublic();

        if(mixinClassEditor.getAnnotationEditor(Type.getDescriptor(Extends.class)) != null) {
            mixinClassEditor.removeMethod(mixinMethodEditor);
            return;
        }

        BytecodeEditor bytecodeEditor = mixinMethodEditor.getBytecodeEditor();
        for (int i = 0; i < bytecodeEditor.getBytecode().size(); i++) {
            bytecodeEditor.remove(i);
        }

        bytecodeEditor.add(0, new TypeInsnNode(NEW, targetClassEditor.getName()));
        bytecodeEditor.add(0, new InsnNode(DUP));

        List<AbstractInsnNode> abstractInsnNodes = new ArrayList<>();
        TransformerHelper.addLoadOpcodesOfMethod(abstractInsnNodes, Type.getArgumentTypes(targetMethodEditor.getDesc()), true);
        bytecodeEditor.add(0, abstractInsnNodes);

        bytecodeEditor.add(0, new MethodInsnNode(INVOKESPECIAL, targetClassEditor.getName(), targetMethodEditor.getName(), targetMethodEditor.getDesc()));
        bytecodeEditor.add(0,new InsnNode(ARETURN));
    }
}
