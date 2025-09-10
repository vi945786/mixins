package vi.mixin.api.editors;

import org.objectweb.asm.tree.AnnotationNode;
import vi.mixin.util.AnnotationNodeToInstance;

import java.lang.annotation.Annotation;

public class AnnotationEditor {
    final AnnotationNode annotationNode;

    public AnnotationEditor(AnnotationNode annotationNode) {
        this.annotationNode = annotationNode;
    }

    public String getDesc() {
        return annotationNode.desc;
    }

    public <T extends Annotation> T getAnnotation() {
        return AnnotationNodeToInstance.getAnnotation(annotationNode);
    }
}
