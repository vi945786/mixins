package vi.mixin.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vi.mixin.api.annotations.methods.Setter;
import vi.mixin.api.annotations.Mixin;

public class SetterAnnotationTest {

    public static class Target {
        private int value;
        private static int staticValue;
        
        public int getValue() { return value; }
        public static int getStaticValue() { return staticValue; }
    }


    @Test
    public void testSetterAnnotation() {
        Target target = new Target();

        ((TargetSetterAccessor) target).setValue(42);
        assertEquals(42, target.getValue());
    }

    @Test
    public void testStaticSetterAnnotation() {
        TargetSetterAccessor.setStaticValue(42);
        assertEquals(42, Target.getStaticValue());
    }
}


@Mixin(SetterAnnotationTest.Target.class)
interface TargetSetterAccessor {
    @Setter("value")
    void setValue(int v);

    @Setter("staticValue")
    static void setStaticValue(int v) {
    }
}
