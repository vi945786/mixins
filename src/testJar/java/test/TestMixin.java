package test;

import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.New;
import vi.mixin.api.injection.At;
import vi.mixin.api.annotations.methods.Inject;
import vi.mixin.api.injection.Returner;
import vi.mixin.api.injection.ValueReturner;

import static vi.mixin.api.injection.At.Location.*;

@Mixin(name = "test.Test")
public class TestMixin {

    @Shadow("getNumberInternal()I")
    private static int getNumberInternal() {
        return 0;
    }

    @Shadow("changed")
    private Boolean changed;

    @Inject(method = "getNumber()I", at = @At(At.Location.HEAD))
    private void changeNumber(ValueReturner<Integer> returner) {
        returner.setReturnValue(getNumberInternal() + (((TestInnerMixin.TestDoubleInnerMixin) (Object) TestInnerMixin.TestDoubleInnerMixin.newDoubleInner(TestInnerMixin.newInner((Test) (Object) this))).doubleInner ? 1 : 0));
    }

    @Mixin(Test.Inner.class)
    public class TestInnerMixin {

        @New("Ltest/Test;")
        private static Test.Inner newInner(Test test) {
            return null;
        }

        @Mixin(Test.Inner.DoubleInner.class)
        public class TestDoubleInnerMixin {

            @New("Ltest/Test$Inner;")
            private static Test.Inner.DoubleInner newDoubleInner(Test.Inner inner) {
                return null;
            }

            @Shadow("doubleInner")
            private Boolean doubleInner;

            @Inject(method = "printA()V", at = @At(HEAD))
            private void printA(Returner returner) {
                System.out.println("B" + changed);
                returner.doReturn();
            }
        }
    }
}

