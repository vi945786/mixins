package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.editors.*;
import vi.mixin.api.transformers.FieldTransformer;
import vi.mixin.api.transformers.MethodTransformer;

public class ShadowTransformer implements FieldTransformer<Shadow>, MethodTransformer<Shadow> {

    private static void validate(ClassEditor mixinClassEditor, FieldEditor mixinFieldEditor, Shadow mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Shadow " + mixinClassEditor.getName() + "." + mixinFieldEditor.getName();
        FieldEditor target = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        if(target == null) throw new MixinFormatException(name, "target doesn't exist");
        if((target.getAccess() & ACC_STATIC) != (mixinFieldEditor.getAccess() & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((target.getAccess() & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type type = Type.getType(mixinFieldEditor.getDesc());
        if(!type.equals(Type.getType(target.getDesc())) && !type.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types are: " + target.getDesc() + ", " + Type.getType(Object.class));
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, FieldEditor mixinFieldEditor, Shadow mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinFieldEditor, mixinAnnotation, targetClassEditor);
        FieldEditor targetFieldEditor = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        targetFieldEditor.makePublic();
        if((mixinFieldEditor.getAccess() & ACC_FINAL) == 0) targetFieldEditor.makeNonFinal();

        for(ClassEditor classEditor : mixinClassEditor.getAllClassesInHierarchy()) {
            for (MethodEditor method : classEditor.getMethodEditors()) {
                for (int index : method.getBytecodeEditor().getInsnNodesIndexes(AbstractInsnNode.FIELD_INSN, null, mixinClassEditor.getName(), mixinFieldEditor.getName(), null)) {
                    AbstractInsnNode insnNode = method.getBytecodeEditor().getBytecode().get(index);
                    method.getBytecodeEditor().add(index, new FieldInsnNode(insnNode.getOpcode(), targetClassEditor.getName(), targetFieldEditor.getName(), targetFieldEditor.getDesc()));
                    method.getBytecodeEditor().remove(index);
                }
            }
        }
        mixinClassEditor.removeField(mixinFieldEditor);
    }

    private static void validate(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Shadow mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Shadow " + mixinClassEditor.getName() + "." + mixinMethodEditor.getName() + mixinMethodEditor.getDesc();
        MethodEditor target = targetClassEditor.getMethodEditor(mixinAnnotation.value());
        if(target == null) throw new MixinFormatException(name, "target doesn't exist");
        if(target.getName().equals("<init>")) throw new MixinFormatException(name, "shadowing a constructor is not allowed. use @New");
        Type returnType = Type.getReturnType(mixinMethodEditor.getDesc());
        if (!returnType.equals(Type.getReturnType(target.getDesc())) && !returnType.equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid return types are: " + Type.getReturnType(target.getDesc()) + ", " + Type.getType(Object.class));
        if((target.getAccess() & ACC_STATIC) != (mixinMethodEditor.getAccess() & ACC_STATIC)) throw new MixinFormatException(name, "should be " + ((target.getAccess() & ACC_STATIC) != 0 ? "" : "not") + " static");
        Type[] mixinArgumentTypes = Type.getArgumentTypes(mixinMethodEditor.getDesc());
        Type[] targetArgumentTypes = Type.getArgumentTypes(target.getDesc());
        if(mixinArgumentTypes.length != targetArgumentTypes.length) throw new MixinFormatException(name, "there should be " + targetArgumentTypes.length + " arguments");
        for (int i = 0; i < targetArgumentTypes.length; i++) {
            if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) throw new MixinFormatException(name, "valid types for argument number " + i + " are:" + targetArgumentTypes[i] + ", " +Type.getType(Object.class));
        }
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Shadow mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinMethodEditor, mixinAnnotation, targetClassEditor);
        MethodEditor targetMethodEditor = targetClassEditor.getMethodEditor(mixinAnnotation.value());
        targetMethodEditor.makePublic();

        mixinMethodEditor.makeNonAbstract();

        int invokeOpcode = INVOKEVIRTUAL;
        if((targetMethodEditor.getAccess() & ACC_STATIC) != 0) invokeOpcode = INVOKESTATIC;
        else if(targetMethodEditor.getName().equals("<init>")) invokeOpcode = INVOKESPECIAL;
        else if((targetMethodEditor.getAccess() & ACC_INTERFACE) != 0) invokeOpcode = INVOKEINTERFACE;

        for(ClassEditor classEditor : mixinClassEditor.getAllClassesInHierarchy()) {
            for (MethodEditor method : classEditor.getMethodEditors()) {
                for (int index : method.getBytecodeEditor().getInsnNodesIndexes(AbstractInsnNode.METHOD_INSN, null, mixinClassEditor.getName(), mixinMethodEditor.getName(), null)) {
                    method.getBytecodeEditor().add(index, new MethodInsnNode(invokeOpcode, targetClassEditor.getName(), targetMethodEditor.getName(), targetMethodEditor.getDesc()));
                    method.getBytecodeEditor().remove(index);
                }
            }
        }
        mixinClassEditor.removeMethod(mixinMethodEditor);
    }
}
