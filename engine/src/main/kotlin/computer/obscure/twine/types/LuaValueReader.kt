
package computer.obscure.twine.types

import computer.obscure.twine.TwineNative
import computer.obscure.twine.engine.TwineContext
import computer.obscure.twine.natives.TwineEnvironment
import net.hollowcube.luau.LuaState

object LuaValueReader {
    fun read(
        state: LuaState,
        index: Int,
        type: Class<*>?,
        context: TwineContext,
        environment: TwineEnvironment
    ): Any? {
        if (state.isNil(index)) return null

        return when (type) {
            String::class.java -> state.checkString(index)
            Int::class.javaObjectType -> state.checkInteger(index)
            Long::class.javaObjectType -> state.checkNumber(index).toLong()
            Short::class.javaObjectType -> state.checkNumber(index).toInt().toShort()
            Byte::class.javaObjectType -> state.checkNumber(index).toInt().toByte()
            Double::class.javaObjectType -> state.checkNumber(index)
            Float::class.javaObjectType -> state.checkNumber(index).toFloat()
            Boolean::class.javaObjectType -> state.toBoolean(index)

            else -> when {
                type?.let { TwineNative::class.java.isAssignableFrom(it) } == true ->
                    readNative(state, index, context, environment)

                type?.isEnum == true ->
                    readEnum(state, index, type)

                state.isNumber(index) -> state.checkNumber(index)
                state.isBoolean(index) -> state.toBoolean(index)
                state.isTable(index) -> readNative(state, index, context, environment)
                else -> state.checkString(index)
            }
        }
    }

    private fun readNative(
        state: LuaState,
        index: Int,
        context: TwineContext,
        environment: TwineEnvironment
    ): TwineNative {
        state.getField(index, "__twineName")

        val name = try {
            state.checkString(-1)
        } finally {
            state.pop(1)
        }

        return environment.registry[name]
            ?: context.registry[name]
            ?: error("No native registered with name '$name'")
    }

    @Suppress("UNCHECKED_CAST")
    private fun readEnum(
        state: LuaState,
        index: Int,
        type: Class<*>
    ): Enum<*> {
        val name = state.checkString(index)
        return java.lang.Enum.valueOf(
            type as Class<out Enum<*>>,
            name
        )
    }
}
