package vi.mixin.api.transformers;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.editors.*;

import java.lang.annotation.Annotation;

public interface TransformerBuilderTarget<A extends Annotation, AE extends AnnotatedEditor, AN, TM extends TargetMethodEditor, TF extends TargetFieldEditor> {

    TransformerBuilderTransform<A, AE, TM, AN, MethodNode> targetMethod();
    TransformerBuilderTransform<A, AE, TF, AN, FieldNode> targetField();

}
