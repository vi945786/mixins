package vi.mixin.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.ModifyValue;
import vi.mixin.api.injection.At;
import vi.mixin.api.injection.Vars;

public class ModifyValueAnnotationTest {

    public static class ModifyValueTarget {
        public int getX() { return 5; }

        public static int tailExample(boolean early) {
            if (early) return 1;
            return 2;
        }

        public int sumWithLocal(int a, int b) { int i = 3; return a + b + i; }
    }

    @Test
    public void testModifyReturn() {
        ModifyValueTarget t = new ModifyValueTarget();
        assertEquals(7, t.getX());
    }

    @Test
    public void testModifyTail() {
        assertEquals(1, ModifyValueTarget.tailExample(true));
        assertEquals(20, ModifyValueTarget.tailExample(false));
    }

    @Test
    public void testModifyWithVars() {
        assertEquals(63, new ModifyValueTarget().sumWithLocal(4, 53));
    }
}

@SuppressWarnings("all")
@Mixin(ModifyValueAnnotationTest.ModifyValueTarget.class)
class ModifyValueMixin {

    @ModifyValue(value = "getX", at = @At(At.Location.RETURN))
    private Object modifyGetX(int original) {
        return original + 2;
    }

    @ModifyValue(value = "tailExample(Z)I", at = @At(At.Location.TAIL))
    private static Object modifyTail(int original) {
        return original + 18;
    }

    @ModifyValue(value = "sumWithLocal(II)I", at = @At(At.Location.RETURN))
    private Object modifySumWithLocal(int original, Vars vars) {
        return original + vars.<Integer>get(2);
    }
}
