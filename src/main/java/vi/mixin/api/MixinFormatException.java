package vi.mixin.api;

public class MixinFormatException extends RuntimeException {
    public MixinFormatException(String mixinLocation, String message) {
        super(mixinLocation + ": " + message);
    }
}
