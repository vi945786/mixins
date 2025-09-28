package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("all")
public class ShadowAnonymousInnerClassTest {

    public static class Target {
        private int field = 7;
        protected int method(int x) { return x + 10; }
    }

    @Test
    void testShadowsInAnonymousTarget() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = AnonymousShadowMixin.class.getDeclaredMethod("testShadowsInAnonymousTarget", Target.class);
        m.setAccessible(true);
        m.invoke(null, new Target());
    }

    @Test
    void testShadowsInAnonymousObject() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = AnonymousShadowMixin.class.getDeclaredMethod("testShadowsInAnonymousObject", Target.class);
        m.setAccessible(true);
        m.invoke(null, new Target());
    }

    @Test
    void testShadowsInDoubleAnonymous() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = AnonymousShadowMixin.class.getDeclaredMethod("doubleAnonymous", Target.class);
        m.setAccessible(true);
        m.invoke(null, new Target());
    }
}

@SuppressWarnings("all")
@Mixin(ShadowAnonymousInnerClassTest.Target.class)
class AnonymousShadowMixin {

    @Shadow private int field;

    @Shadow private int method(int x) {return 0;}

    private void testShadowsInAnonymousTarget() {
        ShadowAnonymousInnerClassTest.Target anon = new ShadowAnonymousInnerClassTest.Target() {
            @Override
            protected int method(int x) {
                return field;
            }
        };

        assertEquals(field, anon.method(0));
    }

    private void testShadowsInAnonymousObject() {
        Object anon = new Object() {
            @Override
            public int hashCode() {
                return field;
            }
        };

        assertEquals(field, anon.hashCode());
    }

    private void doubleAnonymous() {
        Supplier<ShadowAnonymousInnerClassTest.Target> targetSupplier = new Supplier<ShadowAnonymousInnerClassTest.Target>() {
            @Override
            public ShadowAnonymousInnerClassTest.Target get() {
                return new ShadowAnonymousInnerClassTest.Target() {
                    @Override
                    protected int method(int x) {
                        return field;
                    }
                };
            }
        };

        assertEquals(field, targetSupplier.get().method(0));
    }
}
