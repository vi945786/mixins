package vi.mixin.bytecode;

import java.lang.invoke.*;
import java.util.ArrayList;
import java.util.List;

public class LambdaHandler {

    @SuppressWarnings("unused")
    public static Object wrapper(MethodHandles.Lookup caller, String interfaceMethodName, MethodType factoryType, MethodHandle original, Object... args) throws Throwable {
        List<Object> originalArgs = new ArrayList<>(List.of(caller, interfaceMethodName, factoryType));
        originalArgs.addAll(List.of(args));
        return original.invokeWithArguments(originalArgs);
    }
}
