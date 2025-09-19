package vi.mixin.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vi.mixin.api.annotations.methods.Invoker;
import vi.mixin.api.annotations.Mixin;

public class InvokerAnnotationTest {

    public static class Target {
        private int value = 5;
        private static int staticValue = 10;
        private int add(int x) { return value + x; }
        private static int addStatic(int x) { return staticValue + x; }
    }

    @Test
    public void testInvokerAnnotation() {
        Target target = new Target();
        int result = ((TargetInvokerAccessor) target).add(3);
        assertEquals(8, result);
    }

    @Test
    public void testStaticInvokerAnnotation() {
        int result = TargetInvokerAccessor.callAddStatic(7);
        assertEquals(17, result);
    }
}

@Mixin(InvokerAnnotationTest.Target.class)
interface TargetInvokerAccessor {
    @Invoker
    int add(int x);

    @Invoker("addStatic(I)I")
    static int callAddStatic(int x) { return 0; }
}
