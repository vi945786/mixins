package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.tree.*;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.FieldTransformer;
import vi.mixin.api.transformers.MethodTransformer;

import java.util.ArrayList;
import java.util.List;

public class ShadowTransformer implements FieldTransformer<Shadow>, MethodTransformer<Shadow> {

    @Override
    public void transform(ClassEditor mixinClassEditor, FieldEditor mixinFieldEditor, Shadow mixinAnnotation, ClassEditor targetClassEditor) {
        FieldEditor targetFieldEditor = targetClassEditor.getFieldEditor(mixinAnnotation.value());
        targetFieldEditor.makePublic();

        for(MethodEditor method : mixinClassEditor.getMethodEditors()) {
            for(int index : method.getBytecodeEditor().getInsnNodesIndexes(AbstractInsnNode.FIELD_INSN, -1, mixinClassEditor.getName(), targetFieldEditor.getName(), null)) {
                AbstractInsnNode insnNode = method.getBytecodeEditor().getBytecode().get(index);
                List<AbstractInsnNode> insnNodes = new ArrayList<>();
                insnNodes.add(new FieldInsnNode(insnNode.getOpcode(), targetClassEditor.getName(), targetFieldEditor.getName(), targetFieldEditor.getDesc()));

                method.getBytecodeEditor().add(index, insnNodes);
                method.getBytecodeEditor().removeNode(index);
            }
        }
        mixinClassEditor.removeField(mixinFieldEditor);
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, MethodEditor mixinMethodEditor, Shadow mixinAnnotation, ClassEditor targetClassEditor) {
        MethodEditor targetMethodEditor = targetClassEditor.getMethodEditor(mixinAnnotation.value());
        targetMethodEditor.makePublic();

        mixinMethodEditor.makeNonAbstract();

        int invokeOpcode = INVOKEVIRTUAL;
        if((targetMethodEditor.getAccess() & ACC_STATIC) != 0) invokeOpcode = INVOKESTATIC;
        else if(targetMethodEditor.getName().equals("<init>")) invokeOpcode = INVOKESPECIAL;
        else if((targetMethodEditor.getAccess() & ACC_INTERFACE) != 0) invokeOpcode = INVOKEINTERFACE;

        for(MethodEditor method : mixinClassEditor.getMethodEditors()) {
            for(int index : method.getBytecodeEditor().getInsnNodesIndexes(AbstractInsnNode.METHOD_INSN, -1, mixinClassEditor.getName(), targetMethodEditor.getName(), null)) {
                List<AbstractInsnNode> insnNodes = new ArrayList<>();
                insnNodes.add(new MethodInsnNode(invokeOpcode, targetClassEditor.getName(), targetMethodEditor.getName(), targetMethodEditor.getDesc()));

                method.getBytecodeEditor().add(index, insnNodes);
                method.getBytecodeEditor().removeNode(index);
            }
        }
        mixinClassEditor.removeMethod(mixinMethodEditor);
    }
}
