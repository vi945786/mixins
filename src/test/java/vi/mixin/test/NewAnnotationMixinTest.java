package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.New;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NewAnnotationMixinTest {

    public static class Target {
        private final int value;
        private Target(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    @Test
    public void testNewAnnotation() {
        Target t = TargetNewMixin.createTarget(123);
        assertEquals(123, t.getValue());
    }
}

@Mixin(NewAnnotationMixinTest.Target.class)
class TargetNewMixin {
    @New
    static NewAnnotationMixinTest.Target createTarget(int value) { return null; }
}
