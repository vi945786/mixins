package vi.mixin.api;

import vi.mixin.bytecode.RegisterJars;

@SuppressWarnings("unused")
public class MixinManager {

    /**
     * any classes loaded through reflection or URLClassLoaders must be registered here
     */
    public static void addJarToClasspath(String jar) {
        RegisterJars.addJar(jar);
    }
}
