package vi.mixin.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.Getter;

public class GetterAnnotationTest {

    public static class Target {
        private int value = 7;
        private static int staticValue = 13;
    }

    @Test
    public void testGetterAnnotation() {
        Target target = new Target();
        int v = ((TargetGetterAccessor) target).getValue();
        assertEquals(7, v);
    }

    @Test
    public void testStaticGetterAnnotation() {
        int v = TargetGetterAccessor.getStaticValue();
        assertEquals(13, v);
    }
}

@Mixin(GetterAnnotationTest.Target.class)
interface TargetGetterAccessor {
    @Getter
    int getValue();

    @Getter("staticValue")
    static int getStaticValue() { return 0; }
}
