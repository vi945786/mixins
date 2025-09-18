package vi.mixin.api.transformers.accessortype;

import org.objectweb.asm.tree.*;
import vi.mixin.api.MixinFormatException;
import vi.mixin.api.transformers.*;
import vi.mixin.api.transformers.targeteditors.TargetClassEditor;
import vi.mixin.api.transformers.targeteditors.TargetFieldEditor;
import vi.mixin.api.transformers.targeteditors.TargetMethodEditor;

import java.lang.annotation.Annotation;
import java.util.Map;

public final class AccessorClassTransformer implements ClassTransformer<Annotation, AccessorMethodTransformer, AccessorClassTransformer.AccessorFieldTransformer, AccessorMethodEditor, AccessorFieldEditor> {
    interface AccessorFieldTransformer<A extends Annotation> extends FieldTransformer<AccessorMethodEditor, AccessorFieldEditor, A> { }

    @Override
    public AccessorMethodEditor create(MethodNode mixinMethodNode, TargetMethodEditor[] targetMethodEditors) {
        return new AccessorMethodEditor(mixinMethodNode, targetMethodEditors);
    }

    @Override
    public AccessorMethodEditor create(FieldNode mixinFieldNode, TargetMethodEditor[] targetMethodEditors) {
        return new AccessorMethodEditor(mixinFieldNode, targetMethodEditors);
    }

    @Override
    public AccessorFieldEditor create(MethodNode mixinMethodNode, TargetFieldEditor[] targetFieldEditors) {
        return new AccessorFieldEditor(mixinMethodNode, targetFieldEditors);
    }

    @Override
    public AccessorFieldEditor create(FieldNode mixinFieldNode, TargetFieldEditor[] targetFieldEditors) {
        return new AccessorFieldEditor(mixinFieldNode, targetFieldEditors);
    }

    @Override
    public Class<AccessorMethodTransformer> getMethodTransformerType() {
        return AccessorMethodTransformer.class;
    }

    @Override
    public Class<AccessorFieldTransformer> getFieldTransformerType() {
        return AccessorFieldTransformer.class;
    }

    @Override
    public String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Map<MethodNode, AccessorMethodEditor> methodNodeEditorMap, Map<FieldNode, AccessorFieldEditor> fieldNodeEditorMap, Annotation annotation, TargetClassEditor targetClassEditor) {
        ClassNode mixinClassNode = mixinClassNodeHierarchy.getClassNode();
        targetClassEditor.addInterface(mixinClassNode.name);

        ClassNode targetClassNode = targetClassEditor.getClassNodeClone();
        mixinClassNode.methods.forEach(methodNode -> {
            if((methodNode.access & ACC_ABSTRACT) != 0 || (methodNode.access & ACC_STATIC) != 0) return;

            if(targetClassNode.methods.stream()
                    .anyMatch(m -> m.name.equals(methodNode.name) && m.desc.split("\\)")[0].equals(methodNode.desc.split("\\)")[0])) && (methodNode.access & ACC_ABSTRACT) == 0)
                throw new MixinFormatException(mixinClassNode.name + "." + methodNode.name + methodNode.desc, "concrete method with this name and desc already exists in the target class.");

            ClassNode clone = new ClassNode();
            methodNode.accept(clone);

            TargetMethodEditor targetMethodEditor = targetClassEditor.getMethodEditor(methodNode.name + methodNode.desc);
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
