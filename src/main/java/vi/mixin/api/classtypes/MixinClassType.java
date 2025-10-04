package vi.mixin.api.classtypes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetClassEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

import java.lang.annotation.Annotation;

public interface MixinClassType<A extends Annotation, AM extends AnnotatedMethodEditor, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor> extends Opcodes {

    AM create(MethodNode annotatedMethodNode, Object targetEditor);
    AF create(FieldNode annotatedFieldNode, Object targetEditor);
    TM create(MixinClassTargetMethodEditor targetMethodEditors, Object mixinEditors);
    TF create(MixinClassTargetFieldEditor targetFieldEditors, Object mixinEditors);

    default boolean redefineTargetFirst() {
        return true;
    }

    String transform(ClassNodeHierarchy mixinClassNodeHierarchy, A annotation, MixinClassTargetClassEditor targetClassEditor);
}

