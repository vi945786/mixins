package vi.mixin.bytecode;

import java.io.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class Agent {

    public static Instrumentation agent;

    static {
        if("true".equals(System.getProperty("mixin.stage.main"))) {
            try {
                agent = (Instrumentation) Class.forName(Agent.class.getName(), false, ClassLoader.getSystemClassLoader()).getField("agent").get(null);
            } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) throws IOException, ClassNotFoundException, UnmodifiableClassException {
        agent = inst;

        Class.forName(MixinClassHelper.class.getName()); //static init
        AddToBootloaderSearch.add();
        System.setProperty("mixin.stage.main", "true");
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
