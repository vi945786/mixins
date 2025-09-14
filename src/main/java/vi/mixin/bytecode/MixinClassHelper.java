package vi.mixin.bytecode;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MixinClassHelper {
    private static List<ClassLoader> classLoaders = new ArrayList<>(List.of(ClassLoader.getSystemClassLoader(), ClassLoader.getPlatformClassLoader()));
    private static final Method forName0;

    static {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Field bootClassLoader = Class.forName("jdk.internal.loader.ClassLoaders").getDeclaredField("BOOT_LOADER");
            unsafe.putBoolean(bootClassLoader, 12, true);
            classLoaders.add((ClassLoader) bootClassLoader.get(null));

            forName0 = Class.class.getDeclaredMethod("forName0", String.class, boolean.class, ClassLoader.class, Class.class);
            unsafe.putBoolean(forName0, 12, true);

            Method getThreads = Thread.class.getDeclaredMethod("getThreads");
            unsafe.putBoolean(getThreads, 12, true);
            Thread[] threads = (Thread[]) getThreads.invoke(null);

            Field contextClassLoader = Thread.class.getDeclaredField("contextClassLoader");
            unsafe.putBoolean(contextClassLoader, 12, true);

            for (Thread thread : threads) {
                classLoaders.add((ClassLoader) contextClassLoader.get(thread));
            }
            classLoaders = new ArrayList<>(classLoaders.stream().distinct().filter(Objects::nonNull).toList());
        } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?> loadClass(String name) {
        try {
            return Class.forName(findClass(name).getName(), true, findClass(name).getClassLoader());
        } catch (ClassNotFoundException | NullPointerException e) {
            return null;
        }
    }

    public static Class<?> findClass(String name) {
        name = name.replace("/", ".");
        try {
            return Class.forName(name, true, null);
        } catch (ClassNotFoundException ignored) {}

        for(ClassLoader loader : classLoaders) {
            try {
                return Class.forName(name, true, loader);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }
}
