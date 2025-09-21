package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NestedMixinMethodCallTest {

    public static class Outer {
        public class Inner {}
    }

    @Test
    void testOuterCallsInner() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = OuterMethodCallMixin.class.getDeclaredMethod("callInnerMethod", NestedMixinMethodCallTest.Outer.class);
        m.setAccessible(true);
        m.invoke(null, new NestedMixinMethodCallTest.Outer());
    }

    @Test
    void testInnerCallsOuter() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method m = OuterMethodCallMixin.InnerMethodCallMixin.class.getDeclaredMethod("callOuterMethod", NestedMixinMethodCallTest.Outer.Inner.class);
        m.setAccessible(true);
        m.invoke(null, new NestedMixinMethodCallTest.Outer().new Inner());
    }

        @Test
    void testOuterCallsStaticInner() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = OuterMethodCallMixin.class.getDeclaredMethod("callInnerStaticMethod");
        m.setAccessible(true);
        m.invoke(null);
    }

    @Test
    void testInnerCallsStaticOuter() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method m = OuterMethodCallMixin.InnerMethodCallMixin.class.getDeclaredMethod("callOuterStaticMethod");
        m.setAccessible(true);
        m.invoke(null);
    }

}

@Mixin(NestedMixinMethodCallTest.Outer.class)
abstract class OuterMethodCallMixin {
    private void callInnerMethod() {
        OuterMethodCallMixin.InnerMethodCallMixin inner = (OuterMethodCallMixin.InnerMethodCallMixin) (Object) ((NestedMixinMethodCallTest.Outer) (Object) this).new Inner();
        assertEquals(12, inner.innerMethod());
    }

    private static void callInnerStaticMethod() {
        assertEquals(13, InnerMethodCallMixin.innerStaticMethod());
    }

    private int outerMethod() {
            return 14;
    }

    private static int outerStaticMethod() {
            return 15;
    }

    @Mixin(NestedMixinMethodCallTest.Outer.Inner.class)
    abstract class InnerMethodCallMixin {
        private void callOuterMethod() {
            assertEquals(14, outerMethod());
        }

        private static void callOuterStaticMethod() {
            assertEquals(15, outerStaticMethod());
        }

        private int innerMethod() {
            return 12;
        }

        private static int innerStaticMethod() {
            return 13;
        }
    }
}
