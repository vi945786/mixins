package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("all")
public class AddMethodAccessorTest {

    public static class Target {}

    @Test
    public void testAddedMethod() {
        Target target = new Target();
        int result = ((AddMethodAccessor) target).add(3, 7);
        assertEquals(10, result);
    }
}

@SuppressWarnings("all")
@Mixin(AddMethodAccessorTest.Target.class)
interface AddMethodAccessor {
    default int add(int i1, int i2) {
        return i1 + i2;
    }
}
