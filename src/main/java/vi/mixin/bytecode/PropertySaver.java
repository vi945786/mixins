package vi.mixin.bytecode;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public class PropertySaver {

    private static final ConcurrentHashMap<Object, Object> map;

    static {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Field mapField = System.getProperties().getClass().getDeclaredField("map");
            unsafe.putBoolean(mapField, 12, true);
            map = (ConcurrentHashMap<Object, Object>) mapField.get(System.getProperties());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    public static void set(Object key, Object value) {
        map.put(key, value);
    }

    public static <T> T get(Object key) {
        return (T) map.get(key);
    }
}
