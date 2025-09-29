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

## Annotations

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

## Bytecode Injection Points: `@At`

The `@At` annotation is used to specify precise bytecode injection points for `@Inject`, `@Redirect`, and `@ModifyValue`.\
The supported locations are:

- `HEAD`: The start of the method.
- `RETURN`: All return instructions.
- `TAIL`: The last return instruction.
- `INVOKE`: A specific method invocation (by owner, name, and desc, and optionally opcode).
  - opcodes:
    - `INVOKEVIRTUAL`: instance method calls
    - `INVOKESTATIC`: static method calls
    - `INVOKESPECIAL`: constructor/private/super method calls 
    - `INVOKEINTERFACE`: instance method calls when the compile-time reference is an interface
  - target: declaring class's internal name followed by a period then the method name and method desc 
    - example: `package1/package2/ClassName.method(Ljava/lang/String;)Ljava/lang/String;`
- `FIELD`: A specific field access (by owner, name, desc, and optionally opcode).
  - opcodes:
      - `GETSTATIC`: get the value of a static field
      - `PUTSTATIC`: set the value of a static field
      - `GETFIELD`: get the value of an instance field
      - `PUTFIELD`: set the value of an instance field
  - target: declaring class's internal name followed by a period then the field name and field desc separated by a colon 
    - example: `package1/package2/ClassName.field:Ljava/lang/String;`
- `STORE`: A specific store instruction (optionally by opcode).
  - opcodes:
      - `ISTORE`: store a boolean, byte, char, short, or int to a local variable 
      - `LSTORE`: store a long to a local variable 
      - `FSTORE`: store a float to a local variable 
      - `DSTORE`: store a double to a local variable 
      - `ASTORE`: store an object to a local variable 
  - target: the local variable index
- `LOAD`: A specific load instruction (optionally by opcode).
  - opcodes:
    - `ILOAD`: load a boolean, byte, char, short, or int to a local variable 
    - `LLOAD`: load a long to a local variable 
    - `FLOAD`: load a float to a local variable 
    - `DLOAD`: load a double to a local variable 
    - `ALOAD`: load an object to a local variable 
  - target: the local variable index
- `CONSTANT`: A specific constant (by value). 
  - target: the constant type followed by the value
    - constant types:
      - int (boolean, byte, char, short, or int)
      - long
      - float
      - double
      - string
      - class (the value is in the descriptor format)
    - examples:
      - `"int;0"` (this is also false) 
      - `"int;1"` (this is also true)
      - `"float;3.141"`
      - `"string;abc"`
      - `"class;Ljava/lang/String;"`
- `JUMP`: A specific jump instruction (optionally by opcode).
  - opcodes:
    - `IFEQ`: jump if value == 0
    - `IFNE`: jump if value != 0 
    - `IFLT`: jump if value < 0 
    - `IFGE`: jump if value >= 0 
    - `IFGT`: jump if value > 0 
    - `IFLE`: jump if value <= 0
    - `IF_ICMPEQ`: jump if int1 == int2
    - `IF_ICMPNE`: jump if int1 != int2
    - `IF_ICMPLT`: jump if int1 < int2
    - `IF_ICMPGE`: jump if int1 >= int2
    - `IF_ICMPGT`: jump if int1 > int2
    - `IF_ICMPLE`: jump if int1 <= int2
    - `IF_ACMPEQ`: jump if two references are equal (==)
    - `IF_ACMPNE`: jump if two references are not equal (!=)
    - `GOTO`: always jump
    - `JSR`: jump to subroutine (obsolete since Java 6)
    - `IFNULL`: jump if reference == null
    - `IFNONNULL`: jump if reference = null

Other options:
- `ordinals`: choose which occurrences to match (0-based). If empty, matches all.
- `offset`: shift the injection points forward or backward by the given number of instructions (positive = forward, negative = backward). Fragile across versions, avoid if possible.

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

## Anonymous classes as targets

Anonymous classes compile to names like `full.package.Outer$1`, `full.package.Outer$2` (order-based). \
Target them with `@Mixin(name = "full/package/Outer$N")`.
This is fragile, avoid if possible. 

---

## Lambda functions as target

Lambdas compile to methods named like `lambda$enclosingMethod$0`, `lambda$enclosingMethod$1` (order-based) in the same class.\

Example of injecting a lambda:
  ```java
  @Mixin(Target.class)
  class TargetMixin {
    @Inject(value = "lambda$method$1", at = @At(At.Location.RETURN))
    private static void swapReturn(ValueReturner<Integer> v) {
        v.setReturnValue(3);
    }
  }
  ```

This is fragile, avoid if possible. 

---

## Using the mixin agent arguments

You can specify additional mixin configuration files at runtime using the mixin agent arguments:

```
-javaagent:<mixin jar location>=path/to/mixins1.json;path/to/mixins2.json
```

Separate multiple files with your platform's path separator (`;` on Windows, `:` on Unix).

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

Use this if you load classes instead of reflection or custom class loaders (such as `URLClassLoader`).\
Any JARs that are not on the main classpath must be registered this way to ensure they don't interfere with the mixin system.

---

## Custom Transformers

### Transformers 
A transformer is code that alters a target method or field based on a matching annotated method or field defined in the mixin class.

### Choosing The Mixin Class Type
Pick the type based on what you want to do with the target.

Built-in Mixin Class Types:
- Mixin: use when you want to inject code into a target
- Extender: use when you want to override or modify a method/field in the mixin subclass
- Accessor: use when you want to add a method or add implementation to an abstract method in the target

### Creating A Custom Transformer
To define a custom transformer implement a `TransformerSupplier` with the single method `getBuiltTransformers` which returns a list of built transformers.\
The TransformerSupplier must be registered in the mixin file.

To create a built transformer use the `TransformerBuilder`.\
The usage is as follows:
1. Start with calling the `annotatedMethodTransformerBuilder` or `annotatedFieldTransformerBuilder` method with the chosen MixinClassType and annotation to instantiate a builder.
2. Use `withMethodTarget()` or `withFieldTarget()` to specify the target type.
3. Set the transformation logic with the `setTransformer` method.
4. Optionally Filter applicable targets using the `setTargetFilter` method.
5. finally, call the `build()` method to return a BuiltTransformer which is ready for use.

Many common helper functions related to ASM bytecode manipulation can be found in the `TransformerHelper` class.\
For more advanced bytecode analysis, there is a type-aware version of the ASM `BasicInterpreter` called `TypeAwareBasicInterpreter`, useful when you need more type information during analysis.

---

## Custom Mixin Class Types
will be added soon.\
for now check the java files under the sub paths of `vi.mixin.api.classtypes`