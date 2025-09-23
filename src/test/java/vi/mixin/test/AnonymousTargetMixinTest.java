package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnonymousTargetMixinTest {

    public static class Producer {
        public Object createTargetAnon() {
            return new Object() {
                private int anonField = 9;
                private int anonMethod(int x) { return x + 1; }
            };
        }
    }

    @Test
    void testMixinOnAnonymousTarget() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object anon = new Producer().createTargetAnon();
        Method m = AnonymousTargetMixin.class.getDeclaredMethod("testOnAnon", anon.getClass());
        m.setAccessible(true);
        m.invoke(null, anon);
    }
}

@Mixin(name = "vi/mixin/test/AnonymousTargetMixinTest$Producer$1")
class AnonymousTargetMixin {
    @Shadow private int anonField;

    @Shadow private int anonMethod(int x) { return 0; }

    private void testOnAnon() {
        assertEquals(9, anonField);
        anonField = 77;
        assertEquals(77, anonField);
        assertEquals(11, anonMethod(10));
    }
}
