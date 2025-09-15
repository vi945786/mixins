package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NestedMixinFieldTest {

    public static class Outer {
        public class Inner {}
    }

    @Test
    void testOuterGetsInner() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = OuterFieldMixin.class.getDeclaredMethod("getInnerField", Outer.class);
        m.setAccessible(true);
        m.invoke(null, new Outer());
    }

    @Test
    void testInnerGetsOuter() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method m = OuterFieldMixin.InnerFieldMixin.class.getDeclaredMethod("getOuterField", Outer.Inner.class);
        m.setAccessible(true);
        m.invoke(null, new Outer().new Inner());
    }

        @Test
    void testOuterGetsStaticInner() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = OuterFieldMixin.class.getDeclaredMethod("getInnerStaticField");
        m.setAccessible(true);
        m.invoke(null);
    }

    @Test
    void testInnerGetsStaticOuter() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method m = OuterFieldMixin.InnerFieldMixin.class.getDeclaredMethod("getOuterStaticField");
        m.setAccessible(true);
        m.invoke(null);
    }

}

@Mixin(NestedMixinFieldTest.Outer.class)
abstract class OuterFieldMixin {
    private int outerField = 15;
    private static int staticOuterField = 16;

    private void getInnerField() {
        InnerFieldMixin inner = (InnerFieldMixin) (Object) ((NestedMixinFieldTest.Outer) (Object) this).new Inner();
        assertEquals(24, inner.innerField);
    }

    private static void getInnerStaticField() {
        assertEquals(25, InnerFieldMixin.staticInnerField);
    }

    @Mixin(NestedMixinFieldTest.Outer.Inner.class)
    abstract class InnerFieldMixin {
        private int innerField = 24;
        private static int staticInnerField = 25;

        private void getOuterField() {
            assertEquals(15, outerField);
        }

        private static void getOuterStaticField() {
            assertEquals(16, staticOuterField);
        }
    }
}
