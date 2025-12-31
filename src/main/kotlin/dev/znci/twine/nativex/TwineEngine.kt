package dev.znci.twine.nativex

import dev.znci.twine.TwineError
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

class TwineEngine {
    val globals: Globals = JsePlatform.standardGlobals()

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
     * Runs a script and returns the result.
     */
    fun run(name: String, content: String): LuaValue {
        return globals.load(content, name).call()
    }
}