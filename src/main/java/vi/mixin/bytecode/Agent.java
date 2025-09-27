package vi.mixin.bytecode;

import java.io.*;
import java.lang.instrument.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

public class Agent {

    public static Instrumentation agent;

    static {
        if(!System.getProperty("java.vm.vendor").equals("JetBrains s.r.o."))
            throw new UnsupportedOperationException("Illegal java distribution. Install a version of the JetBrains Runtime: https://github.com/JetBrains/JetBrainsRuntime/releases");

        try {
            agent = (Instrumentation) Class.forName(Agent.class.getName(), false, ClassLoader.getSystemClassLoader()).getField("agent").get(null);
        } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) throws IOException, NoSuchMethodException, IllegalAccessException {
        agent = inst;
        agent.appendToBootstrapClassLoaderSearch(new JarFile(Agent.class.getProtectionDomain().getCodeSource().getLocation().getPath()));

        try {
            Method register = RegisterJars.class.getDeclaredMethod("registerAll", String.class);
            register.setAccessible(true);
            register.invoke(null, agentArgs);
        } catch (InvocationTargetException e) {
            e.getCause().printStackTrace();
        }

        System.gc();
    }

    public static byte[] getBytecode(Class<?> c) {
        AtomicReference<byte[]> bytes = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if(classBeingRedefined == c) {
                    bytes.set(classfileBuffer);
                    latch.countDown();
                }
                return null;
            }
        };

        try {
            Agent.agent.addTransformer(transformer, true);
            Agent.agent.retransformClasses(c);
            latch.await();
            Agent.agent.removeTransformer(transformer);
        } catch (UnmodifiableClassException | InterruptedException ignored) {}

        return bytes.get();
    }
}
