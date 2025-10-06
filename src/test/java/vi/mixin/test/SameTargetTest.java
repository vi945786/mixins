package vi.mixin.test;

import vi.mixin.api.annotations.Mixin;

@SuppressWarnings("all")
public class SameTargetTest {
    public static class Target {}
}

@SuppressWarnings("all")
@Mixin(SameTargetTest.Target.class) class MixinTarget1 {
    private static int num1 = 1;
}

@SuppressWarnings("all")
@Mixin(SameTargetTest.Target.class) class MixinTarget2 {
    private static int num2 = 2;
}
