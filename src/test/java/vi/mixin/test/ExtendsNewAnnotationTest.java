package vi.mixin.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.New;

public class ExtendsNewAnnotationTest {

    public static class Base {
        private final int a, b;
        public Base(int a, int b) {
            this.a = a;
            this.b = b;
        }
        public int sum() { return a + b; }
    }

    @Test
    public void testExtendsAndNewInConstructor() {
        assertTrue(Base.class.isAssignableFrom(BaseNewExtender.class));
        assertEquals(6, ((Base) (Object) new BaseNewExtender()).sum());
    }
}

@Mixin(ExtendsNewAnnotationTest.Base.class) @Extends
class BaseNewExtender {
    @New("II")
    public static void create(int a, int b) {}

    public BaseNewExtender() {
        create(4, 2);
    }
}
