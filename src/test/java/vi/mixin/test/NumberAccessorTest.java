package vi.mixin.test;

import org.junit.jupiter.api.Test;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.Getter;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NumberAccessorTest {
    @Test
    public void testStaticGetterAnnotation() {
        long v = NumberAccessor.getSerialVersionUID();
        assertEquals(-8742448824652078965L, v);
    }
}

@Mixin(Number.class)
interface NumberAccessor {

    @Getter
    static long getSerialVersionUID() {return 0;}
}
