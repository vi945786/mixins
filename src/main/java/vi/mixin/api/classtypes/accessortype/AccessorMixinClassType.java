package vi.mixin.api.classtypes.accessortype;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.classtypes.ClassNodeHierarchy;
import vi.mixin.api.classtypes.Editors;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetClassEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

import java.lang.annotation.Annotation;

public final class AccessorMixinClassType implements MixinClassType<Annotation, AccessorAnnotatedMethodEditor, AnnotatedFieldEditor, AccessorTargetMethodEditor, AccessorTargetFieldEditor> {

    @Override
    public AccessorAnnotatedMethodEditor create(MethodNode mixinMethodNode, Object targetEditor) {
        return new AccessorAnnotatedMethodEditor(mixinMethodNode, targetEditor);
    }

    @Override
    public AnnotatedFieldEditor create(FieldNode mixinFieldNode, Object targetEditor) {
        throw new UnsupportedOperationException("Accessor classes cannot have annotation mixin fields");
    }

    @Override
    public AccessorTargetMethodEditor create(MixinClassTargetMethodEditor targetMethodEditors, Object mixinEditor) {
        return new AccessorTargetMethodEditor(targetMethodEditors, mixinEditor);
    }

    @Override
    public AccessorTargetFieldEditor create(MixinClassTargetFieldEditor targetFieldEditors, Object mixinEditor) {
        return new AccessorTargetFieldEditor(targetFieldEditors, mixinEditor);
    }

    public boolean redefineTargetFirst() {
        return false;
    }

    @Override
    public String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Editors<AccessorAnnotatedMethodEditor, AnnotatedFieldEditor, AccessorTargetMethodEditor, AccessorTargetFieldEditor> editors, Annotation annotation, MixinClassTargetClassEditor targetClassEditor) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.classNode();
        targetClassEditor.addInterface(mixinClassNode.name);
        mixinClassNode.access |= ACC_PUBLIC;
        mixinClassNode.access &= ~ACC_PRIVATE;
        mixinClassNode.access &= ~ACC_PROTECTED;

        ClassNode targetClassNode = targetClassEditor.getClassNodeClone();
        mixinClassNode.methods.forEach(methodNode -> {
            if((methodNode.access & ACC_ABSTRACT) != 0 || (methodNode.access & ACC_STATIC) != 0) return;

            if(targetClassNode.methods.stream().anyMatch(m -> {
                if((methodNode.access & ACC_ABSTRACT) != 0 || !m.name.equals(methodNode.name)) return false;

                Type[] mixinArgumentTypes = Type.getArgumentTypes(methodNode.desc);
                Type[] targetArgumentTypes = Type.getArgumentTypes(m.desc);
                if(mixinArgumentTypes.length != targetArgumentTypes.length) return false;
                for (int i = 0; i < targetArgumentTypes.length; i++) {
                    if (!mixinArgumentTypes[i].equals(targetArgumentTypes[i]) && !mixinArgumentTypes[i].equals(Type.getType(Object.class))) return false;
                }

                return true;
            })) throw new MixinFormatException(mixinClassNode.name + "." + methodNode.name + methodNode.desc, "concrete method with this name and desc already exists in the target class.");

            ClassNode clone = new ClassNode();
            methodNode.accept(clone);

            MixinClassTargetMethodEditor targetMethodEditor = targetClassEditor.getMethodEditor(methodNode.name + methodNode.desc);
            if(targetMethodEditor == null) {
                targetClassEditor.addMethod(clone.methods.getFirst());
            } else {
                targetMethodEditor.makeNonAbstract();
                targetMethodEditor.makePublic();
                targetMethodEditor.getInsnListEditor().add(methodNode.instructions);
            }

            methodNode.instructions = new InsnList();
            methodNode.access |= ACC_ABSTRACT;
        });

        return null;
    }

}
