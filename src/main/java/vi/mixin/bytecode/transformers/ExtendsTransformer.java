package vi.mixin.bytecode.transformers;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.ClassTransformer;

import static vi.mixin.api.transformers.TransformerHelper.getMethodEditor;

public class ExtendsTransformer implements ClassTransformer<Extends> {

    private static void validate(ClassEditor mixinClassEditor, Extends mixinAnnotation, ClassEditor targetClassEditor) {
        String name = "@Extends " + mixinClassEditor.getName();
        if(mixinClassEditor.getSuperName() != null && !mixinClassEditor.getSuperName().equals("java/lang/Object")) throw new MixinFormatException(name, "extends " + mixinClassEditor.getSuperName());
        if((mixinClassEditor.getAccess() & ACC_INTERFACE) != 0) throw new MixinFormatException(name, "@Extends is not allowed on interfaces");

        if(targetClassEditor.getMethodEditor("<init>()V") == null) {
            for (MethodEditor editor : mixinClassEditor.getMethodEditors()) {
                if (!editor.getName().equals("<init>")) continue;
                boolean foundCall = false;
                for(AbstractInsnNode node : editor.getBytecodeEditor().getBytecode()) {
                    if (!(node instanceof MethodInsnNode methodInsnNode)) continue;
                    MethodEditor nodeMethodEditor = getMethodEditor(mixinClassEditor, methodInsnNode);
                    if(nodeMethodEditor != null && nodeMethodEditor.getAnnotationEditor(Type.getDescriptor(New.class)) != null) {
                        foundCall = true;
                        break;
                    }
                }
                if(!foundCall) throw new MixinFormatException(name, "constructor doesn't have call to super class constructor using @New");
            }
        }
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, Extends mixinAnnotation, ClassEditor targetClassEditor) {
        validate(mixinClassEditor, mixinAnnotation, targetClassEditor);
        targetClassEditor.makePublic();
        targetClassEditor.makeNonFinalOrSealed();
        mixinClassEditor.setSuperName(targetClassEditor.getName());

        for (MethodEditor editor : mixinClassEditor.getMethodEditors()) {
            if (!editor.getName().equals("<init>")) continue;
            boolean switched = false;
            for (int i = 0; i < editor.getBytecodeEditor().getBytecode().size(); i++) {
                if (!(editor.getBytecodeEditor().get(i) instanceof MethodInsnNode methodInsnNode)) continue;
                MethodEditor nodeMethodEditor = getMethodEditor(mixinClassEditor, methodInsnNode);
                if(nodeMethodEditor == null || nodeMethodEditor.getAnnotationEditor(Type.getDescriptor(New.class)) == null) continue;

                String newDesc = methodInsnNode.desc;
                newDesc = newDesc.split("\\)")[0] + ")V";

                editor.getBytecodeEditor().add(i, new MethodInsnNode(INVOKESPECIAL, targetClassEditor.getName(), "<init>", newDesc));
                editor.getBytecodeEditor().remove(i);
                switched = true;
            }
            for (int index : editor.getBytecodeEditor().getInsnNodesIndexes(AbstractInsnNode.METHOD_INSN, INVOKESPECIAL, null, null, "<init>", "()V")) {
                if(targetClassEditor.getMethodEditor("<init>()V") != null && !switched) editor.getBytecodeEditor().add(index, new MethodInsnNode(INVOKESPECIAL, targetClassEditor.getName(), "<init>", "()V"));
                editor.getBytecodeEditor().remove(index);
            }
        }
    }
}
