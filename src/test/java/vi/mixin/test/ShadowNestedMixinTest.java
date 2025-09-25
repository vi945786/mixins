package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("all")
public class ShadowNestedMixinTest {

    public static class Outer {
        public int outerField = 10;
        public int outerMethod(int x) { return x + 100; }
        public static int staticOuterField = 20;
        public static int staticOuterMethod(int x) { return x + 200; }

        public class Inner {
            public int innerField = 30;
            public int innerMethod(int x) { return x + 300; }
            public static int staticInnerField = 40;
            public static int staticInnerMethod(int x) { return x + 400; }
        }
    }

    @Test
    void testInnerShadowsInOuter() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = OuterShadowMixin.class.getDeclaredMethod("testInnerShadowsInOuter", ShadowNestedMixinTest.Outer.class);
        m.setAccessible(true);
        m.invoke(null, new ShadowNestedMixinTest.Outer());
    }

    @Test
    void testOuterShadowsInInner() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = OuterShadowMixin.InnerShadowMixin.class.getDeclaredMethod("testOuterShadowsInInner", ShadowNestedMixinTest.Outer.Inner.class);
        m.setAccessible(true);
        m.invoke(null, new ShadowNestedMixinTest.Outer().new Inner());
    }
}

@SuppressWarnings("all")
@Mixin(ShadowNestedMixinTest.Outer.class)
abstract class OuterShadowMixin {
    @Shadow("outerField") private int shadowOuterField;
    @Shadow("outerMethod(I)I") private int shadowOuterMethod(int x) { return 0; }
    @Shadow("staticOuterField") private static int shadowStaticOuterField;
    @Shadow("staticOuterMethod(I)I") private static int shadowStaticOuterMethod(int x) { return 0; }

    private void testInnerShadowsInOuter() {
        InnerShadowMixin inner = (InnerShadowMixin) (Object) ((ShadowNestedMixinTest.Outer) (Object) this).new Inner();

        inner.shadowInnerField = 111;
        assertEquals(111, inner.shadowInnerField);
        assertEquals(315, inner.shadowInnerMethod(15));
        InnerShadowMixin.shadowStaticInnerField = 222;
        assertEquals(222, InnerShadowMixin.shadowStaticInnerField);
        assertEquals(425, InnerShadowMixin.shadowStaticInnerMethod(25));
    }

    @Mixin(ShadowNestedMixinTest.Outer.Inner.class)
    abstract class InnerShadowMixin {
        @Shadow("innerField") private int shadowInnerField;
        @Shadow("innerMethod(I)I") private int shadowInnerMethod(int x) { return 0; }
        @Shadow("staticInnerField") private static int shadowStaticInnerField;
        @Shadow("staticInnerMethod(I)I") private static int shadowStaticInnerMethod(int x) { return 0; }

        private void testOuterShadowsInInner() {
            shadowOuterField = 555;
            assertEquals(555, shadowOuterField);
            assertEquals(165, shadowOuterMethod(65));
            shadowStaticOuterField = 666;
            assertEquals(666, shadowStaticOuterField);
            assertEquals(275, shadowStaticOuterMethod(75));
        }
    }
}
