package vi.mixin.bytecode;

import java.lang.invoke.*;

public class LambdaHandler {

    @SuppressWarnings("unused")
    public static CallSite metafactory(MethodHandles.Lookup caller, String interfaceMethodName, MethodType factoryType, MethodType interfaceMethodType, MethodHandle implementation, MethodType dynamicMethodType) throws LambdaConversionException {
        return LambdaMetafactory.metafactory(caller, interfaceMethodName, factoryType, interfaceMethodType, implementation, dynamicMethodType);
    }

    @SuppressWarnings("unused")
    public static CallSite altMetafactory(MethodHandles.Lookup caller, String interfaceMethodName, MethodType factoryType, Object... args) throws LambdaConversionException {
        return LambdaMetafactory.altMetafactory(caller, interfaceMethodName, factoryType, args);
    }
}
