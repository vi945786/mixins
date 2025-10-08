package vi.mixin.bytecode;

import java.io.IOException;
import java.io.InputStream;

public class MixinClassHelper {

    public static byte[] getBytecode(String name) {
        name = name.replace(".", "/");
        if(!name.endsWith(".class")) name += ".class";
        try (InputStream is = ClassLoader.getSystemResource(name).openStream()) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NullPointerException e) {
            throw new RuntimeException(name + " not found");
        }
    }

    public static Class<?> findClass(String name) {
        if(name.endsWith(".class")) name = name.substring(0, name.length() -5);
        name = name.replace("/", ".");
        try {
            return Class.forName(name, false, null);
        } catch (ClassNotFoundException ignored) {}

        return null;
    }
}
