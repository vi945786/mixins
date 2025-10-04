package vi.mixin.api.editors;

public abstract class TargetEditor {
    protected final Object annotatedEditor;

    protected TargetEditor(Object annotatedEditor) {
        this.annotatedEditor = annotatedEditor;
    }
}
