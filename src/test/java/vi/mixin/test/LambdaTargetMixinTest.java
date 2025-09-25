package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.Inject;
import vi.mixin.api.injection.At;
import vi.mixin.api.injection.ValueReturner;

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
        assertEquals(2, Target.method());
    }
}

@SuppressWarnings("all")
@Mixin(LambdaTargetMixinTest.Target.class)
class LambdaTargetMixin {

    @Inject(value = "lambda$method$0", at = @At(At.Location.RETURN))
    private static void testOnAnon(ValueReturner<Integer> valueReturner) {
        valueReturner.setReturnValue(2);
    }
}
