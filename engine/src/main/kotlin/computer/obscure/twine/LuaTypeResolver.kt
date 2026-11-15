package computer.obscure.twine

import net.hollowcube.luau.LuaState
import net.hollowcube.luau.LuaType
import java.lang.ref.WeakReference
import java.util.Optional
import kotlin.math.floor
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf

/**
 * A utility object responsible for marshalling data between Kotlin and Luau.
 * * Handles type checking, conversion from Luau stack values to Kotlin objects,
 * and pushing Kotlin values onto the Luau stack.
 */
object LuaTypeResolver {
    data class TypeMismatch(val expected: String, val actual: String) {
        override fun toString() = "expected $expected, got $actual"
    }

    private val INTEGER_RANGES: Map<KClass<*>, ClosedFloatingPointRange<Double>> = mapOf(
        Byte::class to Byte.MIN_VALUE.toDouble()..Byte.MAX_VALUE.toDouble(),
        Short::class to Short.MIN_VALUE.toDouble()..Short.MAX_VALUE.toDouble(),
        Int::class to Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble(),
        Long::class to Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble(),
    )

    /**
     * Strictly checks the value at [index] against a Kotlin [type]
     *
     * @return null if the value is nullable, otherwise a [TypeMismatch].
     */
    fun check(L: LuaState, index: Int, type: KType, natives: Map<String, TwineNative>): TypeMismatch? =
        check(L, index, type.classifier as? KClass<*>, type.isMarkedNullable, natives)

    fun check(
        L: LuaState,
        index: Int,
        type: KClass<*>?,
        nullable: Boolean,
        natives: Map<String, TwineNative>
    ): TypeMismatch? {
        if (accepts(L, index, type, nullable, natives)) return null
        return TypeMismatch(expectedName(type), actualName(L, index, natives))
    }

    /**
     * Verifies if the value at the given Luau stack [index] matches the expected Kotlin [type].
     *
     * @param L The current Luau state.
     * @param index The position on the stack to check.
     * @param type The target Kotlin class.
     * @param natives A registry of known [TwineNative] objects.
     * @param nullable Whether nil is an acceptable value.
     * @return True if the Luau value can be converted to the Kotlin type.
     */
    fun matches(
        L: LuaState,
        index: Int,
        type: KClass<*>?,
        natives: Map<String, TwineNative>,
        nullable: Boolean = false
    ): Boolean = accepts(L, index, type, nullable, natives)

    private fun accepts(
        L: LuaState,
        index: Int,
        type: KClass<*>?,
        nullable: Boolean,
        natives: Map<String, TwineNative>
    ): Boolean {
        val luaType = L.type(index)
        if (luaType == LuaType.NIL || luaType == LuaType.NONE) return nullable
        if (type == null || type == Any::class) return true

        val isNumber = luaType == LuaType.NUMBER || luaType == LuaType.INTEGER

        return when {
            type == String::class -> luaType == LuaType.STRING
            type == Boolean::class -> luaType == LuaType.BOOLEAN
            type == Double::class || type == Float::class -> isNumber
            type in INTEGER_RANGES -> isNumber && L.toNumber(index).let { n ->
                n == floor(n) && n in INTEGER_RANGES.getValue(type)
            }
            type == LuaCallback::class -> luaType == LuaType.FUNCTION
            type.java.isEnum -> luaType == LuaType.STRING &&
                type.java.enumConstants.any { (it as Enum<*>).name == L.checkString(index) }
            type == Map::class || type == List::class -> luaType == LuaType.TABLE

            else -> when {
                luaType == LuaType.TABLE -> nativeAt(L, index, natives)?.let(type::isInstance) ?: false
                isNumber -> type.isSuperclassOf(Double::class)
                luaType == LuaType.STRING -> type.isSuperclassOf(String::class)
                luaType == LuaType.BOOLEAN -> type.isSuperclassOf(Boolean::class)
                else -> false
            }
        }
    }

    private fun expectedName(type: KClass<*>?): String = when {
        type == null || type == Any::class -> "any"
        type == String::class -> "string"
        type == Boolean::class -> "boolean"
        type == Double::class || type == Float::class -> "number"
        type in INTEGER_RANGES -> "integer"
        type == LuaCallback::class -> "function"
        type == Map::class || type == List::class -> "table"
        type.java.isEnum -> type.java.enumConstants.joinToString(" | ") { "\"${(it as Enum<*>).name}\"" }
        else -> type.simpleName ?: "native"
    }

    private fun actualName(L: LuaState, index: Int, natives: Map<String, TwineNative>): String =
        when (val luaType = L.type(index)) {
            LuaType.NONE, LuaType.NIL -> "nil"
            LuaType.NUMBER, LuaType.INTEGER -> "number"
            LuaType.TABLE -> nativeAt(L, index, natives)?.let { it::class.simpleName ?: "native" } ?: "table"
            else -> luaType.name.lowercase()
        }

    private fun nativeAt(L: LuaState, index: Int, natives: Map<String, TwineNative>): TwineNative? {
        L.getField(index, "__twineName")
        val name = if (L.type(-1) == LuaType.STRING) L.checkString(-1) else null
        L.pop(1)
        return name?.let { natives[it] }
    }

    /**
     * Converts a Luau value at [index] into a Kotlin object of the specified [type].
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
        // !! MAJOR PATCH !!
        // if the stack value is nil, return Kotlin null IMMEDIATELY!!!

        if (L.isNil(index)) return null
        return when {
            type == String::class -> L.checkString(index)
            type == Int::class -> L.checkInteger(index)
            type == Long::class -> L.checkNumber(index).toLong()
            type == Short::class -> L.checkNumber(index).toInt().toShort()
            type == Byte::class -> L.checkNumber(index).toInt().toByte()
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
     * Pushes a Kotlin [value] onto the Luau stack.
     *
     * @param L The current Luau state.
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
     * Extracts a [TwineNative] from a Luau table by looking up the `__twineName` field.
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
     * Wraps a Luau function at [index] into a [LuaCallback] for later invocation by Kotlin.
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