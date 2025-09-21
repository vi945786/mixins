package vi.mixin.api.classtypes;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.editors.AnnotatedFieldEditor;
import vi.mixin.api.editors.AnnotatedMethodEditor;
import vi.mixin.api.editors.TargetFieldEditor;
import vi.mixin.api.editors.TargetMethodEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetFieldEditor;
import vi.mixin.api.classtypes.targeteditors.MixinClassTargetMethodEditor;

import java.util.Map;

public record Editors<AM extends AnnotatedMethodEditor, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor>(
        Map<MethodNode, AM> mixinMethodEditors, Map<FieldNode, AF> mixinFieldEditors, Map<MixinClassTargetMethodEditor, TM> targetMethodEditors, Map<MixinClassTargetFieldEditor, TF> targetFieldEditors) {
}
