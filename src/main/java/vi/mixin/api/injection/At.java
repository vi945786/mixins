package vi.mixin.api.injection;

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
     * Used for {@link Location#INVOKE}, {@link Location#FIELD}, {@link Location#NEW}
     */
    String target() default "";

    enum Location {
        HEAD,
        RETURN,
        TAIL,
        INVOKE,
        FIELD,
        NEW,
        JUMP
    }
}
