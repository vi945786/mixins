package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.classtypes.extendertype.Extends;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CallSuperTest {
    public interface Interface {
        default int getValue() {
            return 1;
        }
    }

    public static class Base {
        public int getValue() {
            return 2;
        }
    }

    @Test
    public void testGetValue() {
        new CallSuperExtender().testGetValue();
    }
}

@SuppressWarnings("all")
@Mixin(CallSuperTest.Base.class) @Extends
class CallSuperExtender implements CallSuperTest.Interface {

    @Shadow("vi/mixin/test/CallSuperTest$Interface.getValue") private int interfaceGetValue() {return 0;}
    @Shadow("vi/mixin/test/CallSuperTest$Base.getValue") private int superGetValue() {return 0;}

    public int getValue() {
        return 3;
    }

    public void testGetValue() {
        assertEquals(1, interfaceGetValue());
        assertEquals(2, superGetValue());
        assertEquals(3, getValue());
    }
}
