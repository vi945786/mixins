module mixin.main {
    requires java.instrument;
    requires jdk.unsupported;
    requires org.objectweb.asm.util;
    requires java.management;
    requires jdk.attach;

    exports vi.mixin.api;
    exports vi.mixin.api.annotations;
    exports vi.mixin.api.annotations.classes;
    exports vi.mixin.api.annotations.fields;
    exports vi.mixin.api.annotations.methods;
    exports vi.mixin.api.editors;
    exports vi.mixin.api.injection;
    exports vi.mixin.api.transformers;
}