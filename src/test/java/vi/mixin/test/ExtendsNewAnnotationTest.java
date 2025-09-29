package vi.mixin.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.classes.Extends;
import vi.mixin.api.annotations.methods.New;

@SuppressWarnings("all")
public class ExtendsNewAnnotationTest {

    public static final class Base {
        private final int a, b;
        public Base(int a, Integer b) {
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

@SuppressWarnings("all")
@Mixin(ExtendsNewAnnotationTest.Base.class) @Extends
class BaseNewExtender {
    @New("ILjava/lang/Integer;")
    public static void create(int a, Object b) {}

    public BaseNewExtender(int i) {
        create(4, i);
    }

    public BaseNewExtender() {
        this(2);
    }
}
