package vi.mixin.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.classtypes.extendertype.Extends;
import vi.mixin.api.annotations.methods.Overridable;

@SuppressWarnings("all")
public class OverridableAnnotationTest {

    public static class Base {
        private int value;
        public int getValue(int x) { return value + x; }
    }

    @Test
    public void testOverridable() {
        assertTrue(Base.class.isAssignableFrom(OverridableAnnotationTestExtender.class));
        assertEquals(15, new OverridableAnnotationTestExtender().getValue(10));
    }
}

@SuppressWarnings("all")
@Mixin(OverridableAnnotationTest.Base.class) @Extends
class OverridableAnnotationTestExtender {

    @Overridable
    public int getValue(int x) {
        return 15;
    }
}
