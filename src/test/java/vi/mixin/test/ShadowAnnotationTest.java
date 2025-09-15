package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShadowAnnotationTest {

    public static class ShadowTest {
        public int instanceField = 13;
        public static int staticField = 54;
        public int instanceMethod(int x) { return x+1; }
        public static int staticMethod(int x) { return x+2; }

        public int getInstanceField() {
            return instanceField;
        }

        public int invokeInstanceMethod(int x) {
            return instanceMethod(x);
        }
    }

    @Test
    public void testShadowFieldInstance() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Method m = ShadowTestMixin.class.getDeclaredMethod("testShadowFieldInstance", ShadowAnnotationTest.ShadowTest.class);
        m.setAccessible(true);
        m.invoke(null, new ShadowAnnotationTest.ShadowTest());
    }

    @Test
    public void testShadowFieldStatic() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = ShadowTestMixin.class.getDeclaredMethod("testShadowFieldStatic");
        m.setAccessible(true);
        m.invoke(null);
    }

    @Test
    public void testShadowMethodInstance() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = ShadowTestMixin.class.getDeclaredMethod("testShadowMethodInstance", ShadowAnnotationTest.ShadowTest.class);
        m.setAccessible(true);
        m.invoke(null, new ShadowAnnotationTest.ShadowTest());
    }

    @Test
    public void testShadowMethodStatic() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = ShadowTestMixin.class.getDeclaredMethod("testShadowMethodStatic");
        m.setAccessible(true);
        m.invoke(null);
    }
}

@Mixin(ShadowAnnotationTest.ShadowTest.class)
abstract class ShadowTestMixin {
    @Shadow("instanceField")
    private int instanceField;

    @Shadow("staticField")
    private static int staticField;

    @Shadow("instanceMethod(I)I")
    private int instanceMethod(int x) { return 0; }

    @Shadow("staticMethod(I)I")
    private static int staticMethod(int x) { return 0; }

    private void testShadowFieldInstance() {
        assertEquals(13, this.instanceField);
        this.instanceField = 42;
        assertEquals(42, ((ShadowAnnotationTest.ShadowTest) (Object) this).getInstanceField());
    }

    private static void testShadowFieldStatic() {
        assertEquals(54, staticField);
        staticField = 99;
        assertEquals(99, ShadowAnnotationTest.ShadowTest.staticField);
    }

    private void testShadowMethodInstance() {
        assertEquals(((ShadowAnnotationTest.ShadowTest) (Object) this).invokeInstanceMethod(10), instanceMethod(10));
    }

    private static void testShadowMethodStatic() {
        assertEquals(ShadowAnnotationTest.ShadowTest.staticMethod(10), staticMethod(10));
    }
}
