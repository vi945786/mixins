package vi.mixin.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class Agent {

    public static Instrumentation agent;

    static {
        if("true".equals(System.getProperty("mixin.stage.main"))) {
            agent = PropertySaver.get(Instrumentation.class);
        }
    }

    public static void premain(String agentArgs, Instrumentation inst) throws IOException, ClassNotFoundException, UnmodifiableClassException {
        agent = inst;
        PropertySaver.set(Instrumentation.class, agent);

        Class.forName(MixinClassHelper.class.getName()); //static init
        injectLauncherHelper();
        AddToBootloaderSearch.add();
        System.setProperty("mixin.stage.main", "true");
    }

    private static void injectLauncherHelper() throws ClassNotFoundException, UnmodifiableClassException {
        ClassReader targetClassReader = new ClassReader(Agent.getBytecode(MixinClassHelper.findClass("sun.launcher.LauncherHelper")));
        ClassWriter targetClassWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassNode targetClassNode = new ClassNode();
        targetClassReader.accept(targetClassNode, 0);

        MethodNode loadMainClass = targetClassNode.methods.stream().filter(node -> node.name.equals("loadMainClass")).findAny().orElseThrow();
        AbstractInsnNode getClassLoaderNode = Arrays.stream(loadMainClass.instructions.toArray()).filter(node -> node instanceof MethodInsnNode methodInsnNode && methodInsnNode.name.equals("getSystemClassLoader")).findAny().orElseThrow();
        loadMainClass.instructions.insert(getClassLoaderNode, new InsnNode(Opcodes.ACONST_NULL));
        loadMainClass.instructions.remove(getClassLoaderNode);

        targetClassNode.accept(targetClassWriter);
        agent.redefineClasses(new ClassDefinition(MixinClassHelper.findClass("sun.launcher.LauncherHelper"), targetClassWriter.toByteArray()));
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
