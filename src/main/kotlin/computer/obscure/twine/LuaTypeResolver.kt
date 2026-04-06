package computer.obscure.twine

import net.hollowcube.luau.LuaState
import java.lang.ref.WeakReference
import java.util.Optional
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * A utility object responsible for marshalling data between Kotlin and Luau.
 * * Handles type checking, conversion from Lua stack values to Kotlin objects,
 * and pushing Kotlin values onto the Lua stack.
 */
object LuaTypeResolver {
    /**
     * Verifies if the value at the given Lua stack [index] matches the expected Kotlin [type].
     *
     * @param L The current Lua state.
     * @param index The position on the stack to check.
     * @param type The target Kotlin class.
     * @param natives A registry of known [TwineNative] objects.
     * @return True if the Lua value can be converted to the Kotlin type.
     */
    fun matches(L: LuaState, index: Int, type: KClass<*>?, natives: Map<String, TwineNative>): Boolean {
        return when {
            type == String::class -> L.isString(index)
            type == Double::class || type == Float::class -> L.isNumber(index)
            type == Boolean::class -> L.isBoolean(index)

            // Enums are represented as strings in Luau
            type != null && type.java.isEnum -> L.isString(index)
            type != null && type.isSubclassOf(TwineNative::class) -> L.isTable(index)
            type == Any::class -> true
            type == LuaCallback::class -> L.isFunction(index)
            else -> false
        }
    }

    /**
     * Converts a Lua value at [index] into a Kotlin object of the specified [type].
     *
     * @throws IllegalStateException If the type is a native object and no matching registration exists.
     * @return The converted Kotlin object, or null.
     */
    fun read(
        L: LuaState,
        index: Int,
        type: KClass<*>?,
        natives: Map<String, TwineNative>,
        engine: WeakReference<TwineEngine>? = null
    ): Any? {
        return when {
            type == String::class -> L.checkString(index)
            type == Int::class -> L.checkInteger(index)
            type == Long::class -> L.checkInteger(index)
            type == Double::class -> L.checkNumber(index)
            type == Float::class -> L.checkNumber(index).toFloat()
            type == Boolean::class -> L.toBoolean(index)
            type == LuaCallback::class -> readCallback(L, index, engine)
            type != null && type.isSubclassOf(TwineNative::class) -> readNative(L, index, natives)

            type != null && type.java.isEnum -> {
                val name = L.checkString(index)
                java.lang.Enum.valueOf(type.java as Class<out Enum<*>>, name)
            }
            else -> when {
                L.isNumber(index) -> L.checkNumber(index)
                L.isBoolean(index) -> L.toBoolean(index)
                L.isTable(index) -> readNative(L, index, natives)
                else -> L.checkString(index)
            }
        }
    }

    /**
     * Pushes a Kotlin [value] onto the Lua stack.
     *
     * @param L The current Lua state.
     * @param value The object to push.
     * @param type Optional type hint for the value.
     * @param pushTable A lambda to handle pushing [TwineNative] objects (usually creates a proxied table).
     * @return The number of items pushed onto the stack (usually 1).
     */
    fun push(L: LuaState, value: Any?, type: KClass<*>?, pushTable: (LuaState, TwineNative) -> Unit): Int {
        if (value == null) {
            L.pushNil()
            return 1
        }

        return when (value) {
            is Boolean -> {
                L.pushBoolean(value)
                1
            }
            is Number -> {
                if (value is Double || value is Float) {
                    L.pushNumber(value.toDouble())
                } else {
                    L.pushInteger(value.toInt())
                }
                1
            }
            is TwineNative -> {
                pushTable(L, value)
                1
            }
            is String -> {
                L.pushString(value)
                1
            }
            is Map<*, *> -> {
                L.newTable()
                value.forEach { (k, v) ->
                    L.pushString(k.toString())
                    push(L, v, v?.let { it::class }, pushTable)
                    L.setTable(-3) // table[key] = value
                }
                1
            }
            is Iterable<*> -> {
                L.newTable()
                value.forEachIndexed { index, item ->
                    push(L, item, item?.let { it::class }, pushTable)
                    L.rawSetI(-2, index + 1)
                }
                1
            }

            is Array<*> -> {
                L.newTable()
                value.forEachIndexed { index, item ->
                    push(L, item, item?.let { it::class }, pushTable)
                    L.rawSetI(-2, index + 1)
                }
                1
            }

            is Optional<*> -> {
                push(L, value.orElse(null), null, pushTable)
            }
            else -> {
                // Default to string representations of Enums and other unknown objects
                L.pushString(value.toString())
                1
            }
        }
    }

    /**
     * Extracts a [TwineNative] from a Lua table by looking up the `__twineName` field.
     *
     * @param L The current [LuaState].
     * @param index The stack index where the Luau table is located.
     * @param natives The registry of known native objects.
     * @return The mapped [TwineNative] instance.
     * @throws IllegalStateException If the `__twineName` is missing or not registered.
     */
    private fun readNative(L: LuaState, index: Int, natives: Map<String, TwineNative>): TwineNative? {
        L.getField(index, "__twineName")
        if (L.isNil(-1)) {
            L.pop(1)
            return null
        }
        val name = L.checkString(-1)
        L.pop(1)
        return natives[name] ?: error("No native registered with name '$name'")
    }

    /**
     * Wraps a Lua function at [index] into a [LuaCallback] for later invocation by Kotlin.
     * @param L The current [LuaState].
     * @param index The stack index where the Luau function resides.
     * @return A [LuaCallback] handle that can be invoked from Kotlin later.
     */
    private fun readCallback(L: LuaState, index: Int, engine: WeakReference<TwineEngine>?): LuaCallback {
        val engRef = engine ?: error("Cannot capture LuaCallback: TwineEngine reference is missing")
        if (engRef.get() == null) error("Cannot capture LuaCallback: TwineEngine has been garbage collected")

        return LuaCallback.capture(L, index, engRef)
    }
}