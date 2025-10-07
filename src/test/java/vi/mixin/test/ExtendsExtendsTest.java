package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.classtypes.extendertype.Extends;
import vi.mixin.api.annotations.methods.Overridable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("all")
public class ExtendsExtendsTest {
    public static final class Base {
        protected final int getValue1() {
            return 1;
        }

        protected final int getValue2() {
            return 1;
        }
    }

    @Test
    public void testExtends() {
        assertTrue(ExtendsExtendsTest.Base.class.isAssignableFrom(BaseExtender.class));

        assertEquals(1, new ExtendsExtendsTest.Base().getValue1());
        assertEquals(2, ((ExtendsExtendsTest.Base) (Object) new BaseExtender()).getValue1());

        assertEquals(1, ((ExtendsExtendsTest.Base) (Object) new BaseExtender()).getValue2());
    }

    @Test
    public void testExtendsExtends() {
        assertTrue(BaseExtender.class.isAssignableFrom(BaseExtenderExtender.class));

        assertEquals(3, ((ExtendsExtendsTest.Base) (Object) new BaseExtenderExtender()).getValue1());
        assertEquals(3, ((Base) (Object) new BaseExtenderExtender()).getValue2());
    }
}

@SuppressWarnings("all")
@Mixin(ExtendsExtendsTest.Base.class) @Extends
class BaseExtender {

    @Overridable
    public int getValue1() {
        return 2;
    }
}

@SuppressWarnings("all")
@Mixin(BaseExtender.class) @Extends
class BaseExtenderExtender {

    public int getValue1() {
        return 3;
    }

    @Overridable
    public int getValue2() {
        return 3;
    }
}
