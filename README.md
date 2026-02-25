# Twine 🧵

**A framework that sits on top of LuaJ to provide a better developer experience**

Twine is a Kotlin-first wrapper around LuaJ that makes exposing Java/Kotlin code to Lua scripts significantly less painful. Instead of wrestling with LuaJ's reflection and type conversion, you write annotated Kotlin classes and Twine handles the rest.

## Features

- **Annotation-based bindings** - Mark methods and properties with `@TwineNativeFunction` and `@TwineNativeProperty` to automatically expose them to Lua
- **Automatic type conversion** - Handles conversion between Kotlin and Lua types (strings, numbers, booleans, callbacks, varargs, enums)
- **Method overloading support** - Call the correct overloaded method based on argument types
- **TwineEngine** - Dedicated engine wrapper for managing LuaJ globals and native registration
- **Better error handling** - Clearer error messages than raw LuaJ

## Installation

```kotlin
repositories {
    maven("https://repo.obscure.computer/#browse/browse:maven-public")
}

dependencies {
    implementation("dev.znci:twine:latest.release")
}
```

## Quick Start

### 1. Create a TwineNative class

```kotlin
import computer.obscure.twine.annotations.TwineNativeFunction
import computer.obscure.twine.annotations.TwineNativeProperty
import computer.obscure.twine.nativex.TwineNative

class MyNative : TwineNative() {
    @TwineNativeFunction
    fun greet(name: String): String {
        return "Hello, $name!"
    }
    
    @TwineNativeProperty
    val version: String
        get() = "1.0.0"
    
    @TwineNativeFunction
    fun sum(vararg numbers: Double): Double {
        return numbers.sum()
    }
    
    @TwineNativeFunction
    fun runCallback(callback: (String) -> String): String {
        return callback("from Kotlin")
    }
}
```

### 2. Use TwineEngine to run Lua scripts

```kotlin
import computer.obscure.twine.nativex.TwineEngine

val engine = TwineEngine()
val native = MyNative()

// Register as a table
engine.set("mylib", native)

val result = engine.run("script.lua", """
    return mylib.greet("World")
""").getOrThrow()

println(result) // "Hello, World!"
```

### 3. Register as global base (flatten into global scope)

```kotlin
val engine = TwineEngine()
val native = MyNative()

// Register directly into global scope (no table prefix)
engine.setBase(native)

val result = engine.run("script.lua", """
    print(version)  -- Access property directly
    return greet("World")  -- Call function directly
""").getOrThrow()
```

## Advanced Usage

### Varargs

Methods with `vararg` parameters automatically accept multiple arguments from Lua:

```kotlin
@TwineNativeFunction
fun concat(vararg words: String): String {
    return words.joinToString(" ")
}
```

```lua
-- From Lua
print(mylib.concat("foo", "bar", "baz"))  -- "foo bar baz"
```

### Callbacks

Twine supports Lua functions as Kotlin lambdas:

```kotlin
@TwineNativeFunction
fun delayed(seconds: Int, callback: (Int) -> Unit) {
    Thread.sleep(seconds * 1000L)
    callback(seconds)
}
```

```lua
-- From Lua
mylib.delayed(2, function(elapsed)
    print("Waited " .. elapsed .. " seconds")
end)
```

### Enums

Expose enums to Lua with `TwineEnum`:

```kotlin
enum class State { IDLE, RUNNING, STOPPED }

class MyNative : TwineNative() {
    @TwineNativeProperty
    val state = TwineEnum(State::class)
    
    @TwineNativeFunction
    fun setState(newState: State) {
        println("State changed to: $newState")
    }
}
```

```lua
-- From Lua
mylib.setState(mylib.state.RUNNING)
```

### Method Overloading

Twine automatically dispatches to the correct overload based on argument types:

```kotlin
@TwineNativeFunction
fun process(value: String): String {
    return "String: $value"
}

@TwineNativeFunction
fun process(value: Double): String {
    return "Number: $value"
}
```

```lua
print(mylib.process("hello"))  -- "String: hello"
print(mylib.process(42))       -- "Number: 42.0"
```

## Legacy Usage (without TwineEngine)

You can still use Twine with raw LuaJ globals if needed:

```kotlin
import org.luaj.vm2.lib.jse.JsePlatform

val globals = JsePlatform.standardGlobals()
val native = MyNative()

// Register as a table
globals.set("mylib", native.table)

val result = globals.load("return mylib.greet('World')", "script.lua").call()
println(result) // "Hello, World!"
```

## Error Handling

TwineEngine returns a `Result<Any>` that you can handle:

```kotlin
val result = engine.run("script.lua", """
    error("Something went wrong!")
""")

result.onSuccess { value ->
    println("Success: $value")
}.onFailure { error ->
    println("Error: ${error.message}")
}
```

## Contributing

Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests on the [GitHub repository](https://github.com/obscurecomputer/twine).

## License

Twine is open-source and available under the [Apache License 2.0](LICENSE).
