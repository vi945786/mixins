package vi.mixin.test;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.annotations.methods.ModifyValue;
import vi.mixin.api.injection.At;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("all")
public class MixinMixinTest {
    public static class Target {
        private int i = 1;

        private int getI() {
            return i;
        }

        private int getValue() {
            int i = this.i;
            return i;
        }

        public class Inner {
            private int getValue() {
                int i = Target.this.i;
                return i;
            }
        }
    }

    @Test
    public void testMixinMixin() {
        assertEquals(6, new Target().getValue());
    }

    @Test
    public void testInnerMixinMixin() {
        assertEquals(6, new Target().new Inner().getValue());
    }
}

@SuppressWarnings("all")
@Mixin(MixinMixinTest.Target.class)
class InjectTargetMixin {

    @Shadow private int getI() {return 0;};

    private int i = 2;

    private int getI2() {
        return getI();
    }

    @ModifyValue(value = "getValue", at = @At(value = At.Location.FIELD, target = "vi/mixin/test/MixinMixinTest$Target.i:I", opcode = Opcodes.GETFIELD, offset = 1))
    public Object getValueInject(int i) {
        return i + this.i;
    }

    @Mixin(MixinMixinTest.Target.Inner.class)
    public class Inner {
        @ModifyValue(value = "getValue", at = @At(value = At.Location.FIELD, target = "vi/mixin/test/MixinMixinTest$Target.i:I", opcode = Opcodes.GETFIELD, offset = 1))
        public Object getInnerValueInject(int i) {
            return i + InjectTargetMixin.this.i;
        }
    }
}

@SuppressWarnings("all")
@Mixin(InjectTargetMixin.class)
class InjectTargetMixinMixin {

    @Shadow("vi/mixin/test/MixinMixinTest$Target.i") private int i;
    @Shadow("vi/mixin/test/InjectTargetMixin.i") private int i2;
    @Shadow private int getI2() {return 0;}

    @ModifyValue(value = "getValueInject", at = @At(At.Location.RETURN))
    public Object getValueInjectInject(Integer i) throws NoSuchFieldException, IllegalAccessException {
        return i + i2 - this.i + getI2() + 1;
    }

    @Mixin(InjectTargetMixin.Inner.class)
    private class Inner {
        @ModifyValue(value = "getInnerValueInject", at = @At(At.Location.RETURN))
        public Object getInnerValueInjectInject(Integer i) {
            return i + InjectTargetMixinMixin.this.i + getI2() + 1;
        }
    }
}
