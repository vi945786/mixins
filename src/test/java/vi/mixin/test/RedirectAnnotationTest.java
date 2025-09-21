package vi.mixin.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.objectweb.asm.Opcodes;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.Redirect;
import vi.mixin.api.injection.At;

public class RedirectAnnotationTest {

    public static class RedirectTest {
        public String call() {
            return helper("in");
        }

        private String helper(String s) {
            return "original:" + s;
        }

        public static String callStatic() {
            return helperStatic("s");
        }

        private static String helperStatic(String s) {
            return "origStatic:" + s;
        }
    }

    @Test
    public void redirectInstanceInvoke() {
        assertEquals("redirected:in", new RedirectTest().call());
    }

    @Test
    public void redirectStaticInvoke() {
        assertEquals("redirectedStatic:s", RedirectTest.callStatic());
    }
}

@Mixin(RedirectAnnotationTest.RedirectTest.class)
class RedirectTestMixin {
    @Redirect(value = "call", at = @At(value = At.Location.INVOKE, target = "vi/mixin/test/RedirectAnnotationTest$RedirectTest.helper(Ljava/lang/String;)Ljava/lang/String;", opcode = Opcodes.INVOKEVIRTUAL))
    private String redirectHelper(RedirectAnnotationTest.RedirectTest redirectTest, String s) {
        return "redirected:" + s;
    }

    @Redirect(value = "callStatic", at = @At(value = At.Location.INVOKE, target = "vi/mixin/test/RedirectAnnotationTest$RedirectTest.helperStatic(Ljava/lang/String;)Ljava/lang/String;", opcode = Opcodes.INVOKESTATIC))
    public static String redirectStatic(String s) {
        return "redirectedStatic:" + s;
    }
}