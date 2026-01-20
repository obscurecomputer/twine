package computer.obscure.twine.nativex

import computer.obscure.twine.TwineError
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

class TwineEngine {
    private var _globals: Globals = JsePlatform.standardGlobals()
    val globals: Globals get() = _globals

    /**
     * A regex list containing replacements for traditional LuaJ error messages.
     */
    val errorHandlers: List<Pair<Regex, (MatchResult, String, String) -> String>> = listOf(
        // "attempt to index ?" errors
        Regex("""attempt to index \? \(a nil value\)""") to { _, scriptName, rawMessage ->
            val line = rawMessage.substringAfter(":").substringBefore(" ")
            "Lua error in $scriptName:$line — attempted to index a nil value"
        },
    )

    /**
     * Wipes the globals using a given [Globals] instance.
     *
     * `newGlobals` parameter defaults to a fresh, bare [Globals] instance, containing no
     * default LuaJ globals. Use `JsePlatform.standardGlobals()` in this parameter if this is not
     * what you want.
     */
    fun clear(newGlobals: Globals = Globals()) {
        _globals = newGlobals
    }

    /**
     * Check if the globals contains a [LuaValue] by name.
     */
    fun has(name: String): Boolean =
        !globals.get(name).isnil()

    /**
     * Check if the globals contains a [LuaValue] by a [TwineNative].
     */
    fun has(native: TwineNative): Boolean =
        !globals.get(native.valueName).isnil()

    /**
     * Get a [LuaValue] from the globals by its name.
     */
    fun get(name: String): LuaValue =
        globals.get(name)

    /**
     * Get a [LuaValue] from the globals by a [TwineNative].
     */
    fun get(native: TwineNative): LuaValue =
        globals.get(native.valueName)

    /**
     * Traditional `set(String, LuaValue)` from [Globals].
     */
    fun set(name: String, value: LuaValue) {
        globals.set(name, value)
    }

    /**
     * Adds a [TwineNative] into the globals.
     */
    fun set(name: String, native: TwineNative) {
        globals.set(name, native.table)
    }

    /**
     * Adds a [TwineNative] into the globals using its [TwineNative.valueName].
     */
    fun set(native: TwineNative) {
        globals.set(native.valueName, native.table)
    }

    /**
     * Adds a vararg of [TwineNative]s into the globals using their [TwineNative.valueName]s.
     */
    fun set(vararg natives: TwineNative) {
        natives.forEach { set(it) }
    }

    /**
     * Removes a [LuaValue] by name from the globals.
     */
    fun remove(name: String) {
        globals.set(name, LuaValue.NIL)
    }

    /**
     * Removes a [LuaValue] by [TwineNative] from the globals.
     */
    fun remove(native: TwineNative) {
        globals.set(native.valueName, LuaValue.NIL)
    }

    /**
     * Removes a list of [LuaValue]s by a list of [TwineNative]s from the globals.
     */
    fun remove(vararg natives: TwineNative) {
        natives.forEach {
            globals.set(it.valueName, LuaValue.NIL)
        }
    }

    /**
     * Exposes a [TwineNative]'s functions and properties directly into the Lua global scope.
     *
     * Unlike [set], which registers the native under a table name (e.g. `test.testProperty`),
     * this method flattens all functions and properties into the global scope.
     *
     * `set("test", native)`:
     * -> `test.testProperty`
     *
     * `setBase(native)`:
     * -> `testProperty`
     */
    fun setBase(native: TwineNative) {
        // functions
        native.table.keys().forEach { luaValue ->
            globals.set(luaValue.toString(), native.table.get(luaValue))
        }

        // properties
        val metatable = native.table.getmetatable()?.checktable()
            ?: throw TwineError("No metatable found on ${native.valueName}!")

        val properties = metatable.get("__properties")?.checktable()
            ?: throw TwineError("No __properties found on metatable of ${native.valueName}!")

        properties.keys().forEach { luaValue ->
            globals.set(luaValue.toString(), native.table.get(luaValue))
        }
    }

    /**
     * Exposes a vararg of [TwineNative]s functions and properties directly into the Lua global scope.
     *
     * Unlike [set], which registers the native under a table name (e.g. `test.testProperty`),
     * this method flattens all functions and properties into the global scope.
     *
     * `set("test", native)`:
     * -> `test.testProperty`
     *
     * `setBase(native)`:
     * -> `testProperty`
     */
    fun setBase(vararg natives: TwineNative) {
        natives.forEach { setBase(it) }
    }

    /**
     * Works functionally the same as LuaJ's default `Globals#load(LuaValue)` function.
     * Loads a [LuaValue] into the globals table.
     *
     * Example:
     * ```
     * engine.load(PackageLib())
     * engine.load(Bit32Lib())
     * engine.load(TableLib())
     * engine.load(CoroutineLib())
     * engine.load(JseMathLib())
     * engine.load(LuajavaLib())
     * ```
     */
    fun load(library: LuaValue) {
        globals.load(library)
    }

    /**
     * Runs a script and returns the result.
     * Contains error handling.
     * Runs [runSafe] under the hood.
     */
    fun run(name: String, content: String): Result<LuaValue> {
        return runSafe(name, content)
    }

    /**
     * Runs a script and returns the result.
     * Contains error handling.
     */
    fun runSafe(name: String, content: String): Result<LuaValue> {
        return try {
            Result.success(runUnsafe(name, content))
        } catch (e: Exception) {
            val rawMessage = e.message ?: "Unknown LuaError"

            val newMessage = errorHandlers.firstOrNull { (regex, _) ->
                regex.containsMatchIn(rawMessage)
            }?.let { (regex, handler) ->
                handler(regex.find(rawMessage)!!, name, rawMessage)
            } ?: rawMessage

            Result.failure(TwineError(newMessage))
        }
    }

    /**
     * Runs a script and returns the result.
     * Contains no error handling.
     */
    fun runUnsafe(name: String, content: String): LuaValue {
        return globals.load(content, name).call()
    }

    /**
     * Allows you to do:
     * ```
     * engine.withGlobals {
     *     set("debug", LuaValue.TRUE)
     * }
     * ```
     */
    inline fun <T> withGlobals(block: Globals.() -> T): T {
        return globals.block()
    }
}