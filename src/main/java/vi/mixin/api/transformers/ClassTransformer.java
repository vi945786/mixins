package vi.mixin.api.transformers;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.transformers.targeteditors.TargetClassEditor;
import vi.mixin.api.transformers.targeteditors.TargetFieldEditor;
import vi.mixin.api.transformers.targeteditors.TargetMethodEditor;

import java.lang.annotation.Annotation;
import java.util.Map;

public non-sealed interface ClassTransformer<A extends Annotation, MT extends MethodTransformer, FT extends FieldTransformer, ME extends MethodEditor, FE extends FieldEditor> extends Transformer {

    ME create(MethodNode mixinMethodNode, TargetMethodEditor[] targetMethodEditors);
    ME create(FieldNode mixinFieldNode, TargetMethodEditor[] targetMethodEditors);

    FE create(MethodNode mixinMethodNode, TargetFieldEditor[] targetFieldEditors);
    FE create(FieldNode mixinFieldNode, TargetFieldEditor[] targetFieldEditors);

    Class<MT> getMethodTransformerType();
    Class<FT> getFieldTransformerType();

    String transform(ClassNodeHierarchy mixinClassNodeHierarchy, Map<MethodNode, ME> methodNodeEditorMap, Map<FieldNode, FE> fieldNodeEditorMap, A annotation, TargetClassEditor targetClassEditor);
}

