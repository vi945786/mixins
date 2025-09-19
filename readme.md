# mixin

**mixin** is an annotation-based runtime bytecode manipulation library for Java. It enables powerful and safe(?) modifications to existing classes at runtime.

## Requirements

- **Java:** JetBrains Runtime (required for enhanced class redefinition)
- **JVM Options:**  
  ```
  -XX:+AllowEnhancedClassRedefinition -Xshare:off -javaagent:<mixin jar location>
  ```
- **Manifest:**  
  Specify the mixin file in your JAR manifest using the `Mixin-Classes-File` attribute.

## Getting Started

1. **Add the Agent:**  
   Add the mixin JAR as a Java agent in your JVM options.

2. **Configure Mixin Classes:**  
	- Create a JSON file listing all your mixin classes and transformers.
   - Reference this file in your JAR manifest.

3. **Write Mixin Classes:**  
   - Annotate your mixin classes with `@Mixin(TargetClass.class)`.
   - Use the provided annotations to inject, shadow, override, or extend target class members.

---



## Types of Mixin Classes

There are three types of mixin classes:

**Mixins**:
	- Abstract, concrete, or static classes annotated with `@Mixin`.
	- Used to inject methods, fields, or logic into the target class.
	- Should not be referenced outside itself

**Extenders**:
	- Classes annotated with both `@Mixin` and `@Extends`.
	- These add the target as the superclass to the mixin class.
	- You can cast an instance of the extender to the type of the target to use the superclass methods that aren't overridden in the mixin.

**Accessors**:
	- Interfaces annotated with `@Mixin`.
	- To use accessors, cast an instance of the target class to the accessor interface.
	- When a mixin class is an interface (accessor), that interface and all its parent interfaces are injected into the target class, including any default methods you define in the accessor.

---

## Mixin File Format

The mixin file is a JSON file that specifies which transformers and mixin classes to use. Example:

```json
{
	"transformers": [
		{
			"transformer": "package.ExampleTransformer",
			"annotation": "package.ExampleAnnotation"
		}
	],
	"mixins": [
		"package.ExampleMixin1",
		"package.ExampleMixin2"
	]
}
```

---

## Annotations & Features

### `@Mixin`

Declares a class as a mixin for the specified target class.

```java
@Mixin(TargetClass.class)
public class TargetMixin { ... }
```

---

### `@Extends`

Makes the mixin class extend the target class.

```java
@Mixin(Base.class) @Extends
public class BaseSubclass { ... }
```

---

### `@Shadow`

Accesses (shadows) fields or methods from the target class.  
- Must be `private`.
- Only valid inside the mixin class or its inner/outer mixin classes.

```java
@Mixin(Target.class)
abstract class TargetMixin {
	@Shadow("fieldName")
	private int shadowedField;

	@Shadow("methodName(I)I")
	private int shadowedMethod(int x) { return 0; }
}
```

---

### `@Getter` / `@Setter`

Generates getter/setter methods for fields in the target class.
- Must be inside an accessor
- Valid everywhere

```java
@Mixin(Target.class)
interface TargetAccessor {
	@Getter("fieldName")
	int getField();

	@Setter("fieldName")
	void setField(int value);
}
```

---

### `@Invoker`

Allows calling private or otherwise inaccessible methods in the target class.
- Must be inside an accessor
- Valid everywhere

```java
@Mixin(Target.class)
interface TargetAccessor {
	@Invoker("methodName(I)I")
	int callMethod(int arg);
}
```

---

### `@New`

Allows construction of new instances of the target class using a specific constructor signature.
- Can be used in any type of mixin.

```java
@Mixin(Target.class)
interface TargetAccessor {
	@New("I") // parameter types only, e.g. "I" for int
	Target create(int value);
}
```

---

### `@Inject`

Injects the bytecode of the annotated method into the target method at the location specified by `@At`.

```java
@Mixin(Target.class)
class MyMixin {
	@Inject(method = "foo", at = @At(At.Location.HEAD))
	public void injectFoo(Target self, Returner ret) {
		// code to inject at the start of foo()
	}
}
```

- The injected method must take all target method arguments plus a `Returner` if the target returns void or `ValueReturner` otherwise as the last argument and must return void.

---

### `@Overridable`

Marks a method in the target class as non-final, allowing it to be overridden in a mixin.

```java
@Mixin(Target.class) @Extends
class TargetSubclass {
	@Overridable("methodName")
	public int methodName(int x) { ... }
}
```

---

## Nested Mixins

Mixins can be nested (inner/outer classes), and shadows can be used between them.  
Example:

```java
@Mixin(Outer.class)
public class OuterMixin {
	@Shadow("outerField")
	private int shadowOuterField;

	@Mixin(Outer.Inner.class)
	class InnerMixin {
		@Shadow("innerField")
		private int shadowInnerField;

		@Shadow("outerField")
		private int shadowOuterField; // shadow from outer
	}
}
```

---

## Bytecode Injection Points: `@At`

The `@At` annotation is used to specify precise bytecode injection points for `@Inject` (and other transformers that will be added later). The supported locations are:

- `HEAD`: The start of the method.
- `RETURN`: All return instructions.
- `TAIL`: The last return instruction.
- `INVOKE`: A specific method invocation (by owner, name, and descriptor).
- `FIELD`: A specific field access (by owner, name, and opcode).
- `NEW`: A specific constructor call (by owner and descriptor).
- `JUMP`: A specific jump instruction (by opcode).

You can also use the `ordinal` parameter to select specific occurrences of the matched instructions.

---

## Using `@Extends` with `@New` for Super Constructor Calls

You can use `@Extends` together with `@New` to inject a super constructor call into the constructor.
This allows you to control how the target class is initialized when extending a new superclass.

- Mandatory when the target of the extender doesn't have a no-args constructor

Example:

```java
@Mixin(Base.class) @Extends
class BaseNewExtender {
	@New("II") // parameter types for Base(int, int)
	public static void create(int a, int b) {}

	public BaseNewExtender() {
		create(4, 2); // This will be replaced with super(4, 2)
	}
}
```

---

## Using Returner and ValueReturner

When writing methods that use the `Returner`, you can use this special parameter to control the return value of the target method:

- Use `Returner` if the target method returns `void`.
- Use `ValueReturner<T>` if the target method returns a value of type `T`.

**How to use:**

- To exit the target method early, call `doReturn()` (for `Returner`) or `setReturnValue(value)` (for `ValueReturner`).

**Example:**

```java
@Inject(method = "foo", at = @At(At.Location.HEAD))
public void injectFoo(Target self, Returner ret) {
	if (someCondition) {
		ret.doReturn(); // skips the rest of foo()
	}
}

@Inject(method = "bar", at = @At(At.Location.HEAD))
public void injectBar(Target self, ValueReturner<String> ret) {
	if (shouldOverride) {
		ret.setReturnValue("custom"); // returns "custom" from bar()
	}
}
```

---


## Gradle Test Configuration for Mixins

To run tests with mixins in a Gradle project using JUnit, add the following block to your `build.gradle`:

```gradle
test {
	def wd = project.projectDir

	jvmArgs += [
		*(project.hasProperty('debug') ? ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=y"] : []),
		"-XX:+AllowEnhancedClassRedefinition",
		"-Xshare:off",
		"-javaagent:${wd}\\build\\libs\\mixin.jar",
		"-Dmixin.files=${wd}\\src\\test\\resources\\mixin.json"
	]

	useJUnitPlatform()
}
```

To debug the mixin Java agent or a custom transformer, start your tests with the `-Pdebug` flag:

```
./gradlew test -Pdebug
```

When you run with `-Pdebug`, the JVM will print:

```
Listening for transport dt_socket at address: <port>
```

IntelliJ will offer an "Attach debugger" option. You must click it to start debugging and step through the mixin agent or transformer code.

---

## Using the -Dmixin.files Argument

You can specify additional mixin configuration files at runtime using the `-Dmixin.files` JVM argument:

```
-Dmixin.files=path/to/mixins1.json;path/to/mixins2.json
```

Separate multiple files with your platform's path separator (`;` on Windows, `:` on Unix). Each file will be loaded and its mixins and transformers registered at startup.

This is useful for adding mixins without modifying the JAR manifest.

---

## MixinManager

The `MixinManager` class provides a utility for registering JARs at runtime:

```java
MixinManager.addJarToClasspath("/path/to/your.jar");
```

Use this if you load classes instead of reflection or custom class loaders (such as `URLClassLoader`). Any JARs that are not on the main classpath must be registered this way to ensure they don't interfere with the mixin system.

---

## Transformer API Reference

This section documents the main public API interfaces and abstract classes in `vi.mixin.api.transformers` and its subpackages. These are the core building blocks for custom bytecode manipulation in mixin. Implementation details are intentionally omitted; only the API surface is described.

For a fuller developer-oriented reference with examples and editor contracts see: `docs/transformers_api.md`.

### Overview

The transformer API is organized into several layers:
- **Transformers**: Interfaces for class, method, and field transformation.
- **Editors**: Abstract classes for editing bytecode structures (methods, fields, classes).
- **Helpers**: Utility methods for working with ASM and mixin internals.
- **Target Editors**: Abstract API for editing target class structures.
- **Extender/Accessor Types**: Specialized transformer interfaces for extending or accessing target classes.

---

### Core Interfaces

#### `Transformer`
- Sealed interface, base for all transformer types.
- Extends ASM `Opcodes`.
- Permits: `ClassTransformer`, `InnerElementTransformer`.

#### `ClassTransformer`
- Non-sealed interface for class-level transformation.
- Type parameters: annotation, method/field transformer/editor types.
- Methods:
  - `create` for method/field editors.
  - `getMethodTransformerType`, `getFieldTransformerType`.
  - `transform` for applying transformations.

#### `InnerElementTransformer`
- Sealed interface for method/field transformation.
- Permits: `MethodTransformer`, `FieldTransformer`.
- Methods for matching targets and transforming elements.
- Enum: `TargetType` (METHOD, FIELD).

#### `MethodTransformer` / `FieldTransformer`
- Non-sealed interfaces for method/field transformation.
- Extend `InnerElementTransformer`.
- Provide default target type methods.

---

### Editor Abstract Classes

#### `MethodEditor` / `FieldEditor`
- Abstract classes for editing methods/fields.
- Hold references to mixin and target nodes.
- Methods for cloning nodes, getting number of targets, and accessing target clones.

#### `ClassNodeHierarchy`
- Utility for representing class hierarchies.
- Methods for traversing and collecting class nodes.

---

### Target Editor API

#### `TargetClassEditor`
- Edits target class structure.
- Holds original and modified class nodes.
- Methods for making public, non-final, adding interfaces, and accessing original nodes.

#### `TargetMethodEditor` / `TargetFieldEditor`
- Edits target method/field structure.
- Methods for making public, non-final, non-abstract, non-synthetic, and cloning nodes.

#### `TargetInsnListEditor`
- Edits instruction lists with opcode state tracking.
- Methods for adding, inserting, removing instructions, and cloning instruction lists.

---

### Extender Types

#### `ExtenderClassTransformer`, `ExtenderMethodTransformer`, `ExtenderFieldTransformer`
- Specialized transformer interfaces for extender mixins.
- Methods for creating extender editors and validating mixin/extender constraints.

#### `ExtenderMethodEditor` / `ExtenderFieldEditor`
- Editors for extender mixins.
- Methods for changing invocation/get/set instructions, deleting, and making targets public/non-final.

---

### Accessor Types

#### `AccessorMethodTransformer`
- Interface for accessor method transformation.

#### `AccessorMethodEditor` / `AccessorFieldEditor`
- Editors for accessor mixins.
- Methods for making targets public/non-final and setting mixin bytecode.

---

### Helper Classes

#### `TransformerHelper`
- Utility class for opcode selection, loading arguments, and target node matching.
- Methods for getting return/load opcodes, adding load instructions, and finding target nodes.

---

**Note:** This section only covers the public API surface. For implementation details, refer to the source code.

---

For more advanced usage, see the test files in `src/test/java/vi/mixin/test/`.

