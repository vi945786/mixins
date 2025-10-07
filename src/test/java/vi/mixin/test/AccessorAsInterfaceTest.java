package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.classtypes.extendertype.Extends;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("all")
public class AccessorAsInterfaceTest {

    private static interface Interface {
        default int getValue1() {
            return 1;
        }

        int getValue2();
    }

    @Test
    public void testExtendsAnnotation() {
        assertEquals(2, ((Interface) new Implementation()).getValue1());
        assertEquals(2, ((Interface) new Implementation()).getValue2());
    }
}

@SuppressWarnings("all")
@Mixin(name = "vi/mixin/test/AccessorAsInterfaceTest$Interface") @Extends
interface InterfaceExtender {

    default int getValue1() {
        return 2;
    }

    int getValue2();
}

@SuppressWarnings("all")
class Implementation implements InterfaceExtender {
    public int getValue2() {
        return 2;
    }
}