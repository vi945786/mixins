package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.classtypes.extendertype.Extends;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CallSuperTest {
    public static class Base {
        public int getValue() {
            return 1;
        }
    }

    @Test
    public void testGetValue() {
        new CallSuperExtender().testGetValue();
    }
}

@SuppressWarnings("all")
@Mixin(CallSuperTest.Base.class) @Extends
class CallSuperExtender {

    @Shadow("getValue") private int superGetValue1() {return 0;}

    public int getValue() {
        return 2;
    }

    public void testGetValue() {
        assertEquals(1, superGetValue1());
        assertEquals(2, getValue());
    }
}
