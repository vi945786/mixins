package test;

import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.injection.At;
import vi.mixin.api.annotations.methods.Inject;
import vi.mixin.api.injection.ValueReturner;

@Mixin(Test.class)
public class TestMixin {

    @Shadow("getNumberInternal()I")
    private static int getNumberInternal() {
        return 0;
    }

    @Shadow("changed")
    private Boolean changed;

    @Inject(method = "getNumber()I", at = @At(At.Location.HEAD))
    private void changeNumber(ValueReturner<Integer> returner) {
        returner.setReturnValue(getNumberInternal() + 1 + (changed ? 1 : 0));
    }
}

