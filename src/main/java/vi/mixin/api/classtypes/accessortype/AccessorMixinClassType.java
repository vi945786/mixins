package vi.mixin.api.classtypes.accessortype;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.classtypes.ClassNodeHierarchy;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.classtypes.targeteditors.TargetClassManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetFieldManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetMethodManipulator;
import vi.mixin.api.util.TransformerHelper;

import java.lang.annotation.Annotation;

import static vi.mixin.api.util.TransformerHelper.addLoadOpcodesOfMethod;

public final class AccessorMixinClassType implements MixinClassType<Annotation, AccessorAnnotatedMethodEditor, AnnotatedFieldEditor, AccessorTargetMethodEditor, AccessorTargetFieldEditor> {

    @Override
    public AccessorAnnotatedMethodEditor create(MethodNode annotatedMethodNode, Object targetEditor) {
        return new AccessorAnnotatedMethodEditor(annotatedMethodNode, targetEditor);
    }

    @Override
    public AnnotatedFieldEditor create(FieldNode annotatedFieldNode, Object targetEditor) {
        throw new UnsupportedOperationException("Accessor classes cannot have fields");
    }

    @Override
    public AccessorTargetMethodEditor create(TargetMethodManipulator targetMethodEditors, Object mixinEditor) {
        return new AccessorTargetMethodEditor(targetMethodEditors, mixinEditor);
    }

    @Override
    public AccessorTargetFieldEditor create(TargetFieldManipulator targetFieldEditors, Object mixinEditor) {
        return new AccessorTargetFieldEditor(targetFieldEditors, mixinEditor);
    }

    public boolean redefineTargetFirst() {
        return false;
    }

    @Override
    public void transformBeforeEditors(ClassNodeHierarchy mixinClassNodeHierarchy, Annotation annotation, TargetClassManipulator targetClassManipulator) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.mixinNode();
        targetClassManipulator.addInterface(mixinClassNode.name);
        mixinClassNode.access |= ACC_PUBLIC;
        mixinClassNode.access &= ~ACC_PRIVATE;
        mixinClassNode.access &= ~ACC_PROTECTED;
    }

    @Override
    public String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Annotation annotation, TargetClassManipulator targetClassManipulator) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.mixinNode();

        ClassNode targetClassNode = targetClassManipulator.getClassNodeClone();
        mixinClassNode.methods.forEach(methodNode -> {
            if((methodNode.access & ACC_STATIC) != 0 || (methodNode.access & ACC_ABSTRACT) != 0) return;

            if(targetClassNode.methods.stream().anyMatch(m -> m.name.equals(methodNode.name) && m.desc.equals(methodNode.desc)))
                throw new MixinFormatException(mixinClassNode.name + "." + methodNode.name + methodNode.desc, "method with this name and desc already exists in the target class.");

            MethodNode target = new MethodNode(methodNode.access, methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(String[]::new));
            targetClassManipulator.addMethod(target);

            if((target.access & ACC_ABSTRACT) == 0) {
                int returnOpcode = TransformerHelper.getReturnOpcode(Type.getReturnType(target.desc));
                InsnList insnList = new InsnList();
                addLoadOpcodesOfMethod(insnList, Type.getArgumentTypes(target.desc), false);
                target.instructions.add(insnList);
                target.instructions.add(new MethodInsnNode(INVOKESPECIAL, mixinClassNode.name, methodNode.name, methodNode.desc, true));
                target.instructions.add(new InsnNode(returnOpcode));
            }
        });

        return null;
    }

}
