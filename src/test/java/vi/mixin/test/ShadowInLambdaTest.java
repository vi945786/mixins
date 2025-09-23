package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShadowInLambdaTest {

    public static class Target {
        private int field = 7;
    }

    @Test
    void testShadowsInLambda() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = LambdaShadowMixin.class.getDeclaredMethod("testShadowsInLambda", Target.class);
        m.setAccessible(true);
        m.invoke(null, new Target());
    }
}

@Mixin(ShadowInLambdaTest.Target.class)
class LambdaShadowMixin {

    @Shadow private int field;

    private void testShadowsInLambda() {
        assertEquals(field, get(() -> field));
        assertEquals(field, ((Supplier<Integer>) () -> field).get());
    }

    private int get(Supplier<Integer> a) {
        return a.get();
    }
}
