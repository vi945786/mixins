package vi.mixin.api.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.CLASS)
public @interface At {

    /**
     * which instances of the matching opcodes to mixin
     */
    int[] ordinal() default {};

    Location value();

    /**
     * used for {@link Location#FIELD} and {@link Location#JUMP}
     */
    int opcode() default -1;

    /**
     * Used for {@link Location#INVOKE}, {@link Location#FIELD}
     */
    String target() default "";

    enum Location {
        HEAD,
        RETURN,
        TAIL,
        INVOKE,
        FIELD,
        JUMP
    }
}
