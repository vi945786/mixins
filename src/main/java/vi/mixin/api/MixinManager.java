package vi.mixin.api;

import vi.mixin.api.transformers.MixinTransformer;
import vi.mixin.bytecode.AddToBootloaderSearch;
import vi.mixin.bytecode.Mixiner;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.instrument.UnmodifiableClassException;

public class MixinManager {

    static {
        if(!System.getProperty("java.vm.vendor").equals("JetBrains s.r.o.")) throw new IllegalStateException("Illegal java distribution. Install a distribution of the JetBrains Runtime: https://github.com/JetBrains/JetBrainsRuntime/releases");
    }

    public static void addTransformer(MixinTransformer transformer, Class<? extends Annotation> annotation) {
        Mixiner.addMixinTransformer(transformer, annotation);
    }

    /**
     * any classes loaded through reflection or URLClassLoaders must be registered here
     */
    public static void addJarToClasspath(String jar) {
        try {
            AddToBootloaderSearch.addJar(jar);
        } catch (IOException | UnmodifiableClassException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
