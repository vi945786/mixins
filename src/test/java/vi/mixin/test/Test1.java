package vi.mixin.test;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class Test1 {

    @Test
    public void test() {
        assertTrue(new TargetClass() instanceof MixinClass);
    }
}
