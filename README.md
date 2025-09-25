# Mixin

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

There are three built-in types of mixin classes:

**Mixins**:
- Abstract, concrete, or static classes annotated with `@Mixin`.
- Used to inject logic into the target class.
- Should not be referenced outside itself.

**Extenders**:
- Classes annotated with both `@Mixin` and `@Extends`.
- These set the super class of the mixin class to the target class.
- You can cast an instance of the extender to the type of the target to use the superclass methods that aren't overridden in the mixin.

**Accessors**:
- Interfaces annotated with `@Mixin`.
- To use accessors, cast an instance of the target class to the accessor interface.
- When a mixin class is an interface (accessor), that interface and all its parent interfaces are injected into the target class, including any default methods you define in the accessor
- Abstract methods in the target class will be replaced with the matching accessor method if one exists

---

## Mixin File Format

The mixin file is a JSON file that specifies the mixin class types, transformers, and mixin classes defined in your program. Example:

```json
{
	"mixinClassTypes": [
		"package.ExampleMixinClassType"
  	],
	"transformers": [
		"package.ExampleTransformer"
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
- Can take a string instead of the class in case the class is reachable (for example: `package1/package2/TargetClass`)

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
- Defined for the "Mixin" and "Extender" mixin class types 
- Must be `private`.
- Only valid inside the mixin class or its inner/outer mixin classes.
- Specifying the target is only needed when the name/descriptor doesn't match the target

```java
@Mixin(Target.class)
abstract class TargetMixin {
	@Shadow
	private int shadowedField;

	@Shadow("methodName(I)I")
	private int shadowedMethod(int x) { return 0; }
}
```

---

### `@Getter` / `@Setter`

Generates getter/setter methods for fields in the target class.
- Defined for the "Accessor" mixin class type
- Valid everywhere
- Specifying the target is only needed when the name/descriptor doesn't match the target

```java
@Mixin(Target.class)
interface TargetAccessor {
	@Getter
	int getField();

	@Setter("fieldName")
	void setField(int value);
}
```

---

### `@Invoker`

Allows calling private or otherwise inaccessible methods in the target class.
- Defined the for "Accessor" mixin class type
- Valid everywhere
- Specifying the target is only needed when the name/descriptor doesn't match the target

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
- Defined for the "Mixin", "Accessor", and "Extender" mixin class type
- Specifying the target is only needed when the name/descriptor doesn't match the target

```java
@Mixin(Target.class)
interface TargetAccessor {
	@New("I") // parameter types only, e.g. "I" for int
	Target create(int value);
}
```

---

### `@Overridable`

Marks a method in the target class as non-final, allowing it to be overridden in a mixin.
- Defined for the "Extender" mixin class type
- Specifying the target descriptor is only needed when the descriptor doesn't match the target

```java
@Mixin(Target.class) @Extends
class TargetSubclass {
	@Overridable("I")
	public int methodName(int x) { ... }
}
```

---

### `@Inject`

Injects the bytecode of the annotated method into the target method at the location specified by `@At`.
- Defined for the "Mixin" mixin class type
- Specifying the target descriptor is only needed when the descriptor doesn't match the target
- Supports local variable captures 

```java
@Mixin(Target.class)
class MyMixin {
	@Inject(method = "foo", at = @At(At.Location.HEAD))
	public void injectFoo(Target self, Returner ret) {
		// code to inject at the start of foo()
	}
}
```

The inject method must take all target method arguments plus a `Returner` if the target returns void or `ValueReturner` otherwise as the last argument and must return void.

---

### `@ModifyValue`

Modifies the value at the top of the stack at the location specified by `@At`.
- Defined for the "Mixin" mixin class type
- Specifying the target descriptor is only needed when the descriptor doesn't match the target
- Supports local variable captures 

```java
@Mixin(Target.class)
class MyMixin {
    @ModifyValue(value = "getX", at = @At(At.Location.RETURN))
    private Object modifyGetX(int original) {
        return original + 2;
    }
}
```

The annotated method takes a single value (typed according to the value from the top of the stack) and returns Object. 
The returned type is not enforced but should usually be the same type as was received.

---

### `@Redirect`

Redirects a method call or field access in the target method at the location specified by `@At`.
- Defined for the "Mixin" mixin class type
- The full target descriptor and the opcode is always required
- Valid locations are `INVOKE`, `FIELD`
- Supports local variable captures

```java
@Mixin(Target.class)
class MyMixin {
	@Redirect(value = "method", at = @At(value = At.Location.INVOKE, target = "example/Helper.helper(Ljava/lang/String;)Ljava/lang/String;", opcode = Opcodes.INVOKEVIRTUAL))
    private String redirectHelper(Helper helper, String s) {
        return "redirected:" + s;
    }
}
```

#### Signatures:
> The redirect method signature differs between static or instance redirect methods or fields.

- Static Method Redirects:
  - the signature of the redirected method
- Static Field Get Redirects: 
  - empty signature
- Static Field Set Redirects: 
  - the value which the field will be set to. 

Redirecting instance methods/fields is identical to static methods/fields, just add an additional parameter (always the first in the signature) containing 
the instance which the method is called on. This parameter is of the type that declared the redirected method. 

---

## Using Object as a type fallback

If the real type of a parameter, return value, or field is not accessible from your mixin (for example it's package-private), you may use `Object` as a fallback in your mixin signatures. The mixin system will still match by the provided descriptor.

Example:

```java
@Mixin(Target.class)
interface TargetAccessor { 
	// real signature: private PrivateType clone(PrivateType t)
	@Invoker("clone(Lcom/example/PrivateType;)Lcom/example/PrivateType;") 
	static Object clone(Object t); // use Object when PrivateType isn't visible
}
```

Note that this it will not work for primitives.

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

		private void printOuterField() {
			System.out.println(shadowOuterField);
		}
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
- `JUMP`: A specific jump instruction (by opcode).

You can also use the `ordinal` parameter to select specific occurrences of the matched instructions.\
For examples on how to use check out the tests. 

---

## Using `@Extends` with `@New` for Super Constructor Calls

You can use `@Extends` together with `@New` to inject a super constructor call into the constructor.
This allows you to control how the target class is initialized when extending a new superclass.

- Mandatory when the target of the extender doesn't have a no-args constructor

Example:

```java
@Mixin(Base.class) @Extends
class BaseExtender {
	@New
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
- When injecting to `RETURN` or `TAIL` you can use `ValueReturner.getReturnValue()` to get the return value of the method.

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

## Capturing Local Variables
    
Capturing Local Variables is done by adding an extra parameter of type `Vars` at the end of method signature. 

Vars is a class containing an `Object[]` of captured values. The first values (0 based indexing) are the parameters 
passed into the method, followed by the local variables in order of declaration inside the method. The Vars class 
provides a `<T> T get(int index)` helper method.

examples:
```java
int i = vars.get(1); // implicit type casting
int i = vars.<Integer>get(1); // explicit type casting
```

Capturing Local Variables is supported on methods annotated with any of the following:
`@Inject`, `@Redirect`, `@ModifyValue`

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
		"-javaagent:<mixin jar location>=${wd}\\src\\test\\resources\\mixin.json"
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

## MixinManager

The `MixinManager` class provides a utility for registering JARs at runtime:

```java
MixinManager.addJarToClasspath("/path/to/your.jar");
```

Use this if you load classes instead of reflection or custom class loaders (such as `URLClassLoader`). Any JARs that are not on the main classpath must be registered this way to ensure they don't interfere with the mixin system.

---

## Custom Transformers
will be added soon.\
for now check the java files under the path `vi.mixin.bytecode.transformers`

---

## Custom Mixin Class Types
will be added soon.\
for now check the java files under the sub paths of `vi.mixin.api.classtypes`