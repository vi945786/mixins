package test;

import vi.mixin.api.annotations.Mixin;
import vi.mixin.api.annotations.methods.Invoker;

import java.io.PrintStream;

@Mixin(PrintStream.class)
public interface PrintStreamAccessor {

    @Invoker("newLine()V")
    void newLine();
}
