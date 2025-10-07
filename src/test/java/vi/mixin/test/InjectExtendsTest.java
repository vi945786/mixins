package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.ModifyValue;
import vi.mixin.api.annotations.methods.Overridable;
import vi.mixin.api.classtypes.extendertype.Extends;
import vi.mixin.api.injection.At;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
public class InjectExtendsTest {
    public static final class Base {
        protected final int getValue() {
            return 1;
        }
    }

    @Test
    public void testInjectExtends() {
        assertTrue(InjectExtendsTest.Base.class.isAssignableFrom(InjectBaseExtender.class));

        assertEquals(16, ((Base) (Object) new InjectBaseExtender()).getValue());
    }
}

@SuppressWarnings("all")
@Mixin(InjectExtendsTest.Base.class) @Extends
class InjectBaseExtender {

    @Overridable
    public int getValue() {
        return 4;
    }
}

@SuppressWarnings("all")
@Mixin(InjectBaseExtender.class)
class InjectBaseExtenderMixin {

    @ModifyValue(value = "getValue", at = @At(value = At.Location.RETURN))
    public Object getValue(int i) {
        return i * i;
    }
}
