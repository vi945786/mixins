package vi.mixin.bytecode.anonymoustype;

import org.objectweb.asm.tree.*;
import vi.mixin.api.classtypes.ClassNodeHierarchy;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.classtypes.targeteditors.TargetClassManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetFieldManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetMethodManipulator;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;

import java.lang.annotation.*;

@SuppressWarnings("unused")
class AnonymousMixinClassType implements MixinClassType<AnonymousMixinClassType.Anonymous, AnnotatedMethodEditor, AnnotatedFieldEditor, TargetMethodEditor, TargetFieldEditor> {

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @interface Anonymous {}

    public AnonymousMixinClassType() {}

    @Override
    public AnnotatedMethodEditor create(MethodNode annotatedMethodNode, Object targetEditor) {
        return null;
    }

    @Override
    public AnnotatedFieldEditor create(FieldNode annotatedFieldNode, Object targetEditor) {
        return null;
    }

    @Override
    public TargetMethodEditor create(TargetMethodManipulator targetMethodEditors, Object mixinEditors) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TargetFieldEditor create(TargetFieldManipulator targetFieldEditors, Object mixinEditors) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Anonymous annotation, TargetClassManipulator targetClassManipulator) {
        return null;
    }
}
