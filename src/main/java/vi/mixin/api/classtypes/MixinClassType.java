package vi.mixin.api.classtypes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.TargetClassManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetFieldManipulator;
import vi.mixin.api.classtypes.targeteditors.TargetMethodManipulator;

import java.lang.annotation.Annotation;

public interface MixinClassType<A extends Annotation, AM extends AnnotatedMethodEditor, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor> extends Opcodes {

    AM create(MethodNode annotatedMethodNode, Object targetEditor);
    AF create(FieldNode annotatedFieldNode, Object targetEditor);
    TM create(TargetMethodManipulator targetMethodEditors, Object mixinEditor);
    TF create(TargetFieldManipulator targetFieldEditors, Object mixinEditor);

    default boolean redefineTargetFirst() {
        return true;
    }

    default void transformBeforeEditors(ClassNodeHierarchy mixinClassNodeHierarchy, A annotation, TargetClassManipulator targetClassManipulator) {}
    String transform(ClassNodeHierarchy mixinClassNodeHierarchy, A annotation, TargetClassManipulator targetClassManipulator);
}

