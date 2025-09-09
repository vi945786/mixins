package vi.mixin.util;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import vi.mixin.bytecode.MixinClassHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationNodeToInstance {

     public static <T extends Annotation> T getAnnotation(AnnotationNode node) {
        Class<?> annotationClass = MixinClassHelper.findClass(node.desc.substring(1, node.desc.length() - 1));

         Map<String, Object> values = new HashMap<>();
        if (node.values != null) {
            for (int i = 0; i < node.values.size(); i += 2) {
                String name = (String) node.values.get(i);
                Object value = node.values.get(i + 1);
                values.put(name, convertValue(value));
            }
        }

        InvocationHandler handler = (proxy, method, args) -> {
            if (values.containsKey(method.getName())) {
                return values.get(method.getName());
            }
            return method.getDefaultValue();
        };

        return (T) Proxy.newProxyInstance(
                annotationClass.getClassLoader(),
                new Class[]{annotationClass},
                handler
        );
    }

    private static Object convertValue(Object value) {
        if (value instanceof List list) {
            Object[] arr = new Object[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = convertValue(list.get(i));
            }
            return arr;
        } else if (value instanceof Type type) {
            return MixinClassHelper.findClass(type.getClassName());
        } else if (value instanceof AnnotationNode nested) {
            return getAnnotation(nested);
        } else if (value instanceof String[] enumDesc && enumDesc.length == 2) {
            String className = enumDesc[0].substring(1, enumDesc[0].length() - 1).replace('/', '.');
            Class<?> enumClass = MixinClassHelper.findClass(className);
            return Enum.valueOf((Class<Enum>) enumClass, enumDesc[1]);
        }
        return value;
    }
}
