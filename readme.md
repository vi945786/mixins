mixin is an annotation based runtime bytecode manipulation library for java.

requirements: a JetBrains Runtime.

to use this library just add `-XX:+AllowEnhancedClassRedefinition -Xshare:off -javaagent:<mixin jar location>` to the start of the vm options.
specify the mixinclasses file in the manifest file using the `Mixin-Classes-File` attribute. 
to add a mixin class in the mixin classes file just write the fully qualified name of the class (`package1.package2.Class$InnerClass`)
