package vi.mixin.test;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.objectweb.asm.Opcodes;
import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.Redirect;
import vi.mixin.api.injection.At;
import vi.mixin.api.injection.Vars;

public class RedirectAnnotationTest {

    public static class RedirectTest {
        public String call() {
            return new RedirectFrom().helper("in");
        }

        public static String callStatic() {
            return RedirectFrom.helperStatic("s");
        }

        public static String getInstField(RedirectFrom redirectFrom) {
            return redirectFrom.instField;
        }

        public static String setInstField(RedirectFrom redirectFrom, String v) {
            redirectFrom.instField = v;
            return redirectFrom.instField;
        }

         public static String getStaticField() {
            return RedirectFrom.staticField;
        }

        public static String setStaticField(String v) {
            RedirectFrom.staticField = v;
            return RedirectFrom.staticField;
        }
    }

    public static class RedirectFrom {
        private String helper(String s) {
            return "original:" + s;
        }

        private static String helperStatic(String s) {
            return "origStatic:" + s;
        }

        public String instField = "origInst";
        public static String staticField = "origStatic";
    }

    @Test
    public void redirectInstanceInvoke() {
        assertEquals("redirected:in", new RedirectTest().call());
    }

    @Test
    public void redirectStaticInvoke() {
        assertEquals("redirectedStatic:s", RedirectTest.callStatic());
    }

    @Test
    public void redirectGetInstanceField() {
        assertEquals("redirected:origInst", RedirectTest.getInstField(new RedirectFrom()));
    }

    @Test
    public void redirectSetInstanceField() {
        assertEquals("redirected:newVal", RedirectTest.setInstField(new RedirectFrom(), "newVal"));
    }

    @Test
    public void redirectGetStaticField() {
        assertEquals("redirected:origStatic", RedirectTest.getStaticField());
    }

    @Test
    public void redirectSetStaticField() {
        assertEquals("redirected:newStatic", RedirectTest.setStaticField("newStatic"));
    }
}

@Mixin(RedirectAnnotationTest.RedirectTest.class)
class RedirectTestMixin {
    @Redirect(value = "call", at = @At(value = At.Location.INVOKE, target = "vi/mixin/test/RedirectAnnotationTest$RedirectFrom.helper(Ljava/lang/String;)Ljava/lang/String;", opcode = Opcodes.INVOKEVIRTUAL))
    private String redirectHelper(RedirectAnnotationTest.RedirectFrom redirectFrom, String s) {
        return "redirected:" + s;
    }

    @Redirect(value = "callStatic", at = @At(value = At.Location.INVOKE, target = "vi/mixin/test/RedirectAnnotationTest$RedirectFrom.helperStatic(Ljava/lang/String;)Ljava/lang/String;", opcode = Opcodes.INVOKESTATIC))
    public static String redirectStatic(String s) {
        return "redirectedStatic:" + s;
    }

    @Redirect(value = "getInstField", at = @At(value = At.Location.FIELD, target = "vi/mixin/test/RedirectAnnotationTest$RedirectFrom.instField;Ljava/lang/String;", opcode = Opcodes.GETFIELD))
    private static String redirectGetInstField(RedirectAnnotationTest.RedirectFrom redirectFrom) {
        return "redirected:" + redirectFrom.instField;
    }

    @Redirect(value = "setInstField", at = @At(value = At.Location.FIELD, target = "vi/mixin/test/RedirectAnnotationTest$RedirectFrom.instField;Ljava/lang/String;", opcode = Opcodes.PUTFIELD))
    private static void redirectSetInstField(RedirectAnnotationTest.RedirectFrom redirectFrom, String v, Vars vars) {
        redirectFrom.instField = "redirected:" + v.charAt(0) + vars.<String>get(1).substring(1);
    }

    @Redirect(value = "getStaticField", at = @At(value = At.Location.FIELD, target = "vi/mixin/test/RedirectAnnotationTest$RedirectFrom.staticField;Ljava/lang/String;", opcode = Opcodes.GETSTATIC))
    public static String redirectGetStaticField() {
        return "redirected:" + RedirectAnnotationTest.RedirectFrom.staticField;
    }

    @Redirect(value = "setStaticField", at = @At(value = At.Location.FIELD, target = "vi/mixin/test/RedirectAnnotationTest$RedirectFrom.staticField;Ljava/lang/String;", opcode = Opcodes.PUTSTATIC))
    public static void redirectSetStaticField(String v) {
        RedirectAnnotationTest.RedirectFrom.staticField = "redirected:" + v;
    }
}
