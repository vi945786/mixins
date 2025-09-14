package vi.mixin.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.classes.Extends;

public class ExtendsAnnotationTest {

    public static sealed class SealedBase permits FinalBase {}
    public static final class FinalBase extends SealedBase {}

    @Test
    public void testExtendsAnnotationOnFinalBase() {
        assertTrue(FinalBase.class.isAssignableFrom(FinalBaseExtender.class));
    }

    @Test
    public void testExtendsAnnotationOnSealedBase() {
        assertTrue(SealedBase.class.isAssignableFrom(SealedBaseExtender.class));
    }
}

@Mixin(ExtendsAnnotationTest.FinalBase.class) @Extends
abstract class FinalBaseExtender {}

@Mixin(ExtendsAnnotationTest.SealedBase.class) @Extends
abstract class SealedBaseExtender {}
