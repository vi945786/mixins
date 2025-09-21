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

    AM create(MethodNode mixinMethodNode, Object targetEditor);
    AF create(FieldNode mixinFieldNode, Object targetEditor);
    TM create(MixinClassTargetMethodEditor targetMethodEditors, Object mixinEditors);
    TF create(MixinClassTargetFieldEditor targetFieldEditors, Object mixinEditors);

    String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Editors<AM, AF, TM, TF> editors, A annotation, MixinClassTargetClassEditor targetClassEditor);
}

