package vi.mixin.api.transformers;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import vi.mixin.api.classtypes.MixinClassType;
import vi.mixin.api.editors.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;

public class TransformerBuilder<A extends Annotation, AE extends AnnotatedEditor, TE extends TargetEditor, AN, TN, AM extends AnnotatedMethodEditor, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor> implements TransformerBuilderAnnotation<AM, AF, TM, TF>, TransformerBuilderAnnotated<A, AM, AF, TM, TF>, TransformerBuilderTarget<A, AE, AN, TM, TF>, TransformerBuilderTransform<A, AE, TE, AN, TN>, TransformerBuilderLast<A, AN, TN> {
    private final Class<? extends MixinClassType<?, AM, AF, TM, TF>> mixinClassType;
    private Class<A> annotation = null;
    private Boolean isAnnotatedMethod = null;
    private Boolean isTargetMethod = null;
    private BuiltTransformer.TargetFilter<A, AN, TN> targetFilter = null;
    private BuiltTransformer.TransformFunction<A, AE, TE> transformFunction = null;
    private boolean allowTargetInSuper = false;

    private TransformerBuilder(Class<? extends MixinClassType<?, AM, AF, TM, TF>> mixinClassType) {
        this.mixinClassType = mixinClassType;
    }

    public static <AM extends AnnotatedMethodEditor, AF extends AnnotatedFieldEditor, TM extends TargetMethodEditor, TF extends TargetFieldEditor> TransformerBuilderAnnotation<AM, AF, TM, TF>
    getTransformerBuilder(Class<? extends MixinClassType<?, AM, AF, TM, TF>> mixinClassType) {
        return new TransformerBuilder<>(mixinClassType);
    }

    public <NewA extends Annotation> TransformerBuilderAnnotated<NewA, AM, AF, TM, TF> annotation(Class<NewA> annotation) {
        TransformerBuilder<NewA, AE, TE, AN, TN, AM, AF, TM, TF> builder = new TransformerBuilder<>(mixinClassType);

        builder.isAnnotatedMethod = isAnnotatedMethod;
        builder.isTargetMethod = isTargetMethod;
        builder.annotation = annotation;

        return builder;
    }

    public TransformerBuilderTarget<A, AM, MethodNode, TM, TF> annotatedMethod() {
        TransformerBuilder<A, AM, TE, MethodNode, TN, AM, AF, TM, TF> builder = new TransformerBuilder<>(mixinClassType);

        builder.isAnnotatedMethod = true;
        builder.isTargetMethod = isTargetMethod;
        builder.annotation = annotation;

        return builder;
    }

    public TransformerBuilderTarget<A, AF, FieldNode, TM, TF> annotatedField() {
        TransformerBuilder<A, AF, TE, FieldNode, TN, AM, AF, TM, TF> builder = new TransformerBuilder<>(mixinClassType);

        builder.isAnnotatedMethod = false;
        builder.isTargetMethod = isTargetMethod;
        builder.annotation = annotation;

        return builder;
    }

    public TransformerBuilderTransform<A, AE, TM, AN, MethodNode> targetMethod() {
        TransformerBuilder<A, AE, TM, AN, MethodNode, AM, AF, TM, TF> builder = new TransformerBuilder<>(mixinClassType);

        builder.isAnnotatedMethod = isAnnotatedMethod;
        builder.isTargetMethod = true;
        builder.annotation = annotation;

        return builder;
    }

    public TransformerBuilderTransform<A, AE, TF, AN, FieldNode> targetField() {
        TransformerBuilder<A, AE, TF, AN, FieldNode, AM, AF, TM, TF> builder = new TransformerBuilder<>(mixinClassType);

        builder.isAnnotatedMethod = isAnnotatedMethod;
        builder.isTargetMethod = false;
        builder.annotation = annotation;

        return builder;
    }

    public TransformerBuilderLast<A, AN, TN> transformFunction(BuiltTransformer.TransformFunction<A, AE, TE> transform) {
        this.transformFunction = transform;
        return this;
    }

    public TransformerBuilderLast<A, AN, TN> targetFilter(BuiltTransformer.TargetFilter<A, AN, TN> targetFilter) {
        this.targetFilter = targetFilter;
        return this;
    }

    public TransformerBuilderLast<A, AN, TN> allowTargetsInSuper() {
        this.allowTargetInSuper = true;
        return this;
    }

    @SuppressWarnings("unchecked")
    public BuiltTransformer build() {
        if(targetFilter == null) targetFilter = getDefaultTargetFilter(isAnnotatedMethod, isTargetMethod, allowTargetInSuper);

        return new BuiltTransformer(mixinClassType, annotation, isAnnotatedMethod, isTargetMethod, (BuiltTransformer.TargetFilter<Annotation, Object, Object>) targetFilter, (BuiltTransformer.TransformFunction<Annotation, AnnotatedEditor, TargetEditor>) transformFunction, allowTargetInSuper);
    }

    private static <A extends Annotation, AN, TN> BuiltTransformer.TargetFilter<A, AN, TN> getDefaultTargetFilter(boolean isAnnotatedMethod, boolean isTargetMethod, boolean allowTargetInSuper) {
        if(isAnnotatedMethod && isTargetMethod) {
            return (annotatedNodeClone, targetNodeClone, annotation, origin) -> {
                MethodNode annotatedMethodNodeClone = (MethodNode) annotatedNodeClone;
                MethodNode targetMethodNodeClone = (MethodNode) targetNodeClone;
                try {
                    String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    if (value.isEmpty()) {
                        return targetMethodNodeClone.name.equals(annotatedMethodNodeClone.name) && annotatedMethodNodeClone.desc.equals(targetMethodNodeClone.desc);
                    } else {
                        String className = origin.name;
                        if (allowTargetInSuper && value.contains(".")) {
                            int index = value.indexOf(".");
                            className = value.substring(0, index);
                            value = value.substring(index + 1);
                        }
                        boolean onlyName = !value.contains("(");
                        return (targetMethodNodeClone.name + (onlyName ? "" : targetMethodNodeClone.desc)).equals(value) && className.equals(origin.name);
                    }
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
                    return targetMethodNodeClone.name.equals(annotatedMethodNodeClone.name) && annotatedMethodNodeClone.desc.equals(targetMethodNodeClone.desc);
                }
            };
        } else if(isAnnotatedMethod) {
            return (annotatedNodeClone, targetNodeClone, annotation, origin) -> {
                MethodNode annotatedMethodNodeClone = (MethodNode) annotatedNodeClone;
                FieldNode targetFieldNodeClone = (FieldNode) targetNodeClone;
                try {
                    String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    if (value.isEmpty()) {
                        return annotatedMethodNodeClone.name.equals(targetFieldNodeClone.name);
                    } else {
                        String className = origin.name;
                        if(allowTargetInSuper && value.contains(".")) {
                            int index = value.indexOf(".");
                            className = value.substring(0, index);
                            value = value.substring(index+1);
                        }
                        return (targetFieldNodeClone.name + (value.contains(":") ? ":" + targetFieldNodeClone.desc : "")).equals(value) && className.equals(origin.name);
                    }
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
                    return annotatedMethodNodeClone.name.equals(targetFieldNodeClone.name);
                }
            };
        } else if(isTargetMethod) {
            return (annotatedNodeClone, targetNodeClone, annotation, origin) -> {
                FieldNode annotatedFieldNodeClone = (FieldNode) annotatedNodeClone;
                MethodNode targetMethodNodeClone = (MethodNode) targetNodeClone;
                try {
                    String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    if (value.isEmpty()) {
                        return annotatedFieldNodeClone.name.equals(targetMethodNodeClone.name);
                    } else {
                        String className = origin.name;
                        if(allowTargetInSuper && value.contains(".")) {
                            int index = value.indexOf(".");
                            className = value.substring(0, index);
                            value = value.substring(index+1);
                        }
                        return (targetMethodNodeClone.name + (value.contains(":") ? ":" + targetMethodNodeClone.desc : "")).equals(value) && className.equals(origin.name);
                    }
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
                    return annotatedFieldNodeClone.name.equals(targetMethodNodeClone.name);
                }
            };
        } else {
            return (annotatedNodeClone, targetNodeClone, annotation, origin) -> {
                FieldNode annotatedFieldNodeClone = (FieldNode) annotatedNodeClone;
                FieldNode targetFieldNodeClone = (FieldNode) targetNodeClone;
                try {
                    String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                    if (value.isEmpty()) {
                        return annotatedFieldNodeClone.name.equals(targetFieldNodeClone.name);
                    } else {
                        String className = origin.name;
                        if(allowTargetInSuper && value.contains(".")) {
                            int index = value.indexOf(".");
                            className = value.substring(0, index);
                            value = value.substring(index+1);
                        }
                        return (targetFieldNodeClone.name + (value.contains(":") ? ":" + targetFieldNodeClone.desc : "")).equals(value) && className.equals(origin.name);
                    }
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
                    return annotatedFieldNodeClone.name.equals(targetFieldNodeClone.name);
                }
            };
        }
    }
}
