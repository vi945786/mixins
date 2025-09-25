package vi.mixin.bytecode;

public class MixinClassHelper {

    public static Class<?> findClass(String name) {
        name = name.replace("/", ".");
        try {
            return Class.forName(name, false, null);
        } catch (ClassNotFoundException ignored) {}

        return null;
    }
}
