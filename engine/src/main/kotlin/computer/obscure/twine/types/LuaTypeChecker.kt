
package computer.obscure.twine.types

import computer.obscure.twine.TwineNative
import computer.obscure.twine.natives.BoundFunction
import computer.obscure.twine.natives.ParamSpec
import net.hollowcube.luau.LuaState
import net.hollowcube.luau.LuaType
import kotlin.math.floor

object LuaTypeChecker {
    fun matches(
        state: LuaState,
        index: Int,
        param: ParamSpec
    ): Boolean = matches(state, index, param.klass, param.nullable)

    fun matches(
        state: LuaState,
        index: Int,
        type: Class<*>?,
        nullable: Boolean
    ): Boolean {
        if (type == null) return true

        if (state.isNil(index))
            return nullable

        val luaType = state.type(index)
        val number = luaType == LuaType.NUMBER || luaType == LuaType.INTEGER

        return when (type) {
            Any::class.java -> true
            String::class.java -> luaType == LuaType.STRING
            Boolean::class.javaObjectType -> luaType == LuaType.BOOLEAN

            Double::class.javaObjectType,
            Float::class.javaObjectType -> number

            Int::class.javaObjectType -> integerInRange(
                state, index, Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()
            )

            Long::class.javaObjectType -> integerInRange(
                state, index, Long.MIN_VALUE.toDouble(), Long.MAX_VALUE.toDouble()
            )

            Short::class.javaObjectType -> integerInRange(
                state, index, Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()
            )

            Byte::class.javaObjectType -> integerInRange(
                state, index, Byte.MIN_VALUE.toDouble(), Byte.MAX_VALUE.toDouble()
            )

            else -> when {
                type.isEnum ->
                    luaType == LuaType.STRING && type.enumConstants.any {
                        (it as Enum<*>).name == state.checkString(index)
                    }

                Map::class.java.isAssignableFrom(type) ||
                        List::class.java.isAssignableFrom(type) ->
                    luaType == LuaType.TABLE

                TwineNative::class.java.isAssignableFrom(type) || type.isInterface ->
                    luaType == LuaType.TABLE

                else -> false
            }
        }
    }

    fun matches(
        state: LuaState,
        func: BoundFunction,
        argCount: Int,
        offset: Int
    ): Boolean {
        for (i in 0 until argCount) {
            val param = if (i < func.fixedCount) {
                func.params[i]
            } else {
                func.params.last()
            }

            val type = if (param.isVararg) {
                param.elementClass
            } else {
                param.klass
            }

            val nullable = param.nullable

            if (!matches(state, i + 2 + offset, type, nullable)) {
                return false
            }
        }

        return true
    }

    private fun integerInRange(
        state: LuaState,
        index: Int,
        min: Double,
        max: Double
    ): Boolean {
        if (state.type(index) != LuaType.NUMBER &&
            state.type(index) != LuaType.INTEGER
        ) return false

        val value = state.toNumber(index)
        return value == floor(value) && value >= min && value <= max
    }
}
