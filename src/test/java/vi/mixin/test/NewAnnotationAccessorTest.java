package vi.mixin.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.annotations.Mixin;

public class NewAnnotationAccessorTest {

    public static class Target {
        private final int value;
        private Target(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    @Test
    public void testNewAnnotation() {
        Target t = TargetNewAccessor.createTarget(123);
        assertEquals(123, t.getValue());
    }
}

@Mixin(NewAnnotationAccessorTest.Target.class)
interface TargetNewAccessor {
    @New("I")
    static NewAnnotationAccessorTest.Target createTarget(int value) { return null; }
}
