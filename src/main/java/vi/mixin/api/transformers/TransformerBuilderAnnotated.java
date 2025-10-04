package vi.mixin.api.transformers;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.editors.*;

import java.lang.annotation.Annotation;

public interface TransformerBuilderAnnotated<A extends Annotation, AM extends AnnotatedMethodEditor, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor> {

    TransformerBuilderTarget<A, AM, MethodNode, TM, TF> annotatedMethod();
    TransformerBuilderTarget<A, AF, FieldNode, TM, TF> annotatedField();

}
