package vi.mixin.bytecode.Transformers;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.editors.ClassEditor;
import vi.mixin.api.editors.FieldEditor;
import vi.mixin.api.editors.MethodEditor;
import vi.mixin.api.transformers.ClassTransformer;

import java.util.List;

public class MixinTransformer implements ClassTransformer<Mixin> {

    private static void validate(ClassEditor mixinClassEditor, Mixin mixinAnnotation, ClassEditor targetClassEditor) {
        boolean isExtend = !mixinClassEditor.getAnnotationEditors(Type.getType(Extends.class).getDescriptor()).isEmpty();
        boolean isInterface = (mixinClassEditor.getAccess() & Opcodes.ACC_INTERFACE) != 0;

        for (MethodEditor methodEditor : mixinClassEditor.getMethodEditors()) {
            if (!isInterface && !isExtend && (methodEditor.getAccess() & Opcodes.ACC_PRIVATE) == 0 && !methodEditor.getName().equals("<init>")) throw new MixinFormatException("method " + mixinClassEditor.getName() + "." + methodEditor.getName(), "must be private");
        }

        for (FieldEditor fieldEditor : mixinClassEditor.getFieldEditors()) {
            if (!isInterface && !isExtend && (fieldEditor.getAccess() & Opcodes.ACC_PRIVATE) == 0 && (fieldEditor.getAccess() & Opcodes.ACC_SYNTHETIC) == 0) throw new MixinFormatException("field " + mixinClassEditor.getFieldEditors() + "." + fieldEditor.getName(), "must be private");
        }
    }

    @Override
    public void transform(ClassEditor mixinClassEditor, Mixin mixinAnnotation, ClassEditor targetClassEditor) {
        if ((mixinClassEditor.getAccess() & Opcodes.ACC_INTERFACE) != 0) {
            targetClassEditor.addInterface(mixinClassEditor.getName());
        }

        if(mixinClassEditor.getOuterClass() != null) {
            Type outerType = Type.getType(mixinClassEditor.getTargetClass());

            targetClassEditor.getFieldEditors().stream().filter(fieldNode -> fieldNode.getName().startsWith("this$")).forEach(fieldNode -> {
                if(fieldNode.getDesc().equals(outerType.getDescriptor())) fieldNode.setAccess(fieldNode.getAccess() & ~Opcodes.ACC_SYNTHETIC & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC);
            });
        }

        if(mixinClassEditor.getAnnotationEditors(Type.getType(Extends.class).getDescriptor()).isEmpty()) {
            mixinClassEditor.getMethodEditors().stream().filter(methodEditor -> methodEditor.getName().equals("<init>")).forEach(methodEditor -> {
                List<AbstractInsnNode> bytecode = methodEditor.getBytecodeEditor().getBytecode();
                for (int i = 0; i < bytecode.size(); i++) {
                    AbstractInsnNode insnNode = bytecode.get(i);
                    if (insnNode.getOpcode() != RETURN) continue;

                    methodEditor.getBytecodeEditor().add(i, new TypeInsnNode(NEW, Type.getType(UnsupportedOperationException.class).getInternalName()));
                    methodEditor.getBytecodeEditor().add(i, new InsnNode(DUP));
                    methodEditor.getBytecodeEditor().add(i, new LdcInsnNode("attempted to invoke " + mixinClassEditor.getName() + "." + methodEditor.getName() + methodEditor.getDesc()));
                    methodEditor.getBytecodeEditor().add(i, new MethodInsnNode(INVOKESPECIAL, Type.getType(UnsupportedOperationException.class).getInternalName(), "<init>", "(Ljava/lang/String;)V"));
                    methodEditor.getBytecodeEditor().add(i, new InsnNode(ATHROW));
                    methodEditor.getBytecodeEditor().add(i, new InsnNode(ATHROW));
                    break;
                }
            });
        }

        for (ClassEditor classEditor : mixinClassEditor.getAllClassesInHierarchy()) {
            for (MethodEditor methodEditor : classEditor.getMethodEditors()) {
                for(int index : methodEditor.getBytecodeEditor().getInsnNodesIndexes(AbstractInsnNode.TYPE_INSN, CHECKCAST, mixinClassEditor.getName())) {
                    methodEditor.getBytecodeEditor().remove(index);
                }
            }
        }
    }
}
