package vi.mixin.api.injection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.CLASS)
public @interface At {

    /**
     * Which instance of the matching opcodes to mixin
     */
    int[] ordinals() default {};

    Location value();

    /**
     * Can be used for {@link Location#INVOKE}, {@link Location#FIELD} and {@link Location#JUMP}
     */
    int opcode() default -1;

    /**
     * Can be used for {@link Location#INVOKE}, {@link Location#FIELD}
     */
    String target() default "";

    int offset() default 0;

    enum Location {
        HEAD,
        RETURN,
        TAIL,
        INVOKE,
        FIELD,
        STORE,
        LOAD,
        CONSTANT,
        JUMP
    }
}
