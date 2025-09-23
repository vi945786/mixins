package vi.mixin.bytecode;

import java.lang.invoke.*;

public class LambdaHandler {

    public static CallSite createLambda(MethodHandles.Lookup caller, String interfaceMethodName, MethodType factoryType, MethodType interfaceMethodType, MethodHandle implementation, MethodType dynamicMethodType) throws Exception {
        CallSite site = LambdaMetafactory.metafactory(caller, interfaceMethodName, factoryType, interfaceMethodType, implementation, dynamicMethodType);
        return new MutableCallSite(site.getTarget());
    }
}
