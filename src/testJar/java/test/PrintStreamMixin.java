package test;

import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.Shadow;
import vi.mixin.api.injection.At;
import vi.mixin.api.annotations.methods.Inject;
import vi.mixin.api.injection.Returner;

import java.io.PrintStream;

@Mixin(PrintStream.class)
public abstract class PrintStreamMixin {

    @Shadow("println(Ljava/lang/String;)V") private void println(String s) {};

    @Inject(method = "println()V", at = @At(At.Location.HEAD))
    private void addHelloWorld(Returner returner) {
        println("Hello, World!");
        returner.doReturn();
    }
}
