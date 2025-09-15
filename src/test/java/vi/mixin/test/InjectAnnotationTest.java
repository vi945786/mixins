package vi.mixin.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.Inject;
import vi.mixin.api.injection.At;
import vi.mixin.api.injection.Returner;
import vi.mixin.api.injection.ValueReturner;

public class InjectAnnotationTest {

    public static class InjectTest {
        public StringBuilder sb = new StringBuilder();
        public int sum(int a, int b) { return a + b; }
        public void log(String msg) { sb.append(msg); }
        public static int staticSum(int a, int b) { return a * b; }
        public static StringBuilder staticSb = new StringBuilder();
        public static void staticLog(String msg) { staticSb.append(msg); }
    }

    @Test
    public void testInjectSum() {
        InjectTest t = new InjectTest();
        assertEquals(3, t.sum(5, 2)); // Should be overridden to subtraction
    }

    @Test
    public void testInjectLog() {
        InjectTest t = new InjectTest();
        t.log("hello");
        assertEquals("hello[logged]", t.sb.toString());
    }

    @Test
    public void testInjectStaticSum() {
        assertEquals(24, InjectTest.staticSum(3, 4)); // Should be doubled
    }

    @Test
    public void testInjectStaticLog() {
        InjectTest.staticSb.setLength(0);
        InjectTest.staticLog("hi");
        assertEquals("hi[static]", InjectTest.staticSb.toString());
    }
}

@Mixin(InjectAnnotationTest.InjectTest.class)
class InjectTestMixin {
    @Inject(method = "sum(II)I", at = @At(At.Location.HEAD))
    private void injectSum(int a, int b, ValueReturner<Integer> ret) {
        ret.setReturnValue(a - b);
    }

    @Inject(method = "log(Ljava/lang/String;)V", at = @At(At.Location.RETURN))
    private void injectLog(String msg, Returner ret) {
        ((InjectAnnotationTest.InjectTest) (Object) this).sb.append("[logged]");
    }

    @Inject(method = "staticSum(II)I", at = @At(At.Location.HEAD))
    private static void injectStaticSum(int a, int b, ValueReturner<Integer> ret) {
        ret.setReturnValue(a * b * 2);
    }

    @Inject(method = "staticLog(Ljava/lang/String;)V", at = @At(At.Location.RETURN))
    private static void injectStaticLog(String msg, Returner ret) {
        InjectAnnotationTest.InjectTest.staticSb.append("[static]");
    }
}