package vi.mixin.api.injection;

public class Returner {

    protected boolean returned = false;

    public void doReturn() {
        returned = true;
    }

    public boolean isReturned() {
        return returned;
    }
}
