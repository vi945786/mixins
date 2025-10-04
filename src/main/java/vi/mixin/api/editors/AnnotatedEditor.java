package vi.mixin.api.editors;

public abstract class AnnotatedEditor {
    protected final Object targetEditor;

    protected AnnotatedEditor(Object targetEditor) {
        this.targetEditor = targetEditor;
    }
}
