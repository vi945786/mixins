package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.ModifyValue;
import vi.mixin.api.injection.At;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LambdaTargetMixinTest {

    public static class Target {
        public static int method() {
            Supplier<Integer> lambda = () -> 1;
            return lambda.get();
        }
    }

    @Test
    void testModifyValueLambda() {
        assertEquals(Target.method(), 2);
    }
}

@Mixin(LambdaTargetMixinTest.Target.class)
class LambdaTargetMixin {

    @ModifyValue(value = "lambda$method$0", at = @At(At.Location.RETURN))
    private static Object testOnAnon(Integer i) {
        return 2;
    }
}
