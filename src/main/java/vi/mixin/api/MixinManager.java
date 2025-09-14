package vi.mixin.api;

import vi.mixin.api.transformers.Transformer;
import vi.mixin.bytecode.RegisterJars;
import vi.mixin.bytecode.Mixiner;

import java.lang.annotation.Annotation;

public class MixinManager {

    static {
        if(!System.getProperty("java.vm.vendor").equals("JetBrains s.r.o.")) throw new IllegalStateException("Illegal java distribution. Install a distribution of the JetBrains Runtime: https://github.com/JetBrains/JetBrainsRuntime/releases");
    }

    public static void addTransformer(Transformer transformer, Class<? extends Annotation> annotation) {
        Mixiner.addMixinTransformer(transformer, annotation);
    }

    /**
     * any classes loaded through reflection or URLClassLoaders must be registered here
     */
    public static void addJarToClasspath(String jar) {
        RegisterJars.addJar(jar);
    }
}
