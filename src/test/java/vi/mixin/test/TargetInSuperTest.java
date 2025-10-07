package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("all")
public class TargetInSuperTest {
    public static class Class1 {
        private static int value = 18;
    }

    public static class Class2 extends Class1 {}

    @Test
    public void testShadowInSuper() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = Class2Mixin.class.getDeclaredMethod("testShadowInSuper");
        m.setAccessible(true);
        m.invoke(null);
    }
}

@SuppressWarnings("all")
@Mixin(TargetInSuperTest.Class2.class)
class Class2Mixin {

    @Shadow private static int value;

    private static void testShadowInSuper() {
        assertEquals(18, value);
    }
}
