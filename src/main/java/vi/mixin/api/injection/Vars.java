package vi.mixin.api.injection;

public class Vars {
    private final Object[] v;

    public Vars(Object[] v) {
        this.v = v;
    }

    public <T> T get(int i) {
        return (T) v[i];
    }
}
