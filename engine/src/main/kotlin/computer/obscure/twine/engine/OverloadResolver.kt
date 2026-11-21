package computer.obscure.twine.engine

import computer.obscure.twine.TwineNative
import computer.obscure.twine.exceptions.TwineOverloadException
import computer.obscure.twine.natives.BoundFunction
import computer.obscure.twine.types.LuaTypeChecker
import net.hollowcube.luau.LuaState

object OverloadResolver {
    // if the first arg is self, its colon syntax and we drop self
    // because we already have it :)
    fun offset(L: LuaState, native: TwineNative) = if (L.isTable(2)) {
        L.getField(2, "__twineId")
        val isSelf = !L.isNil(-1) &&
                L.checkString(-1) == native.internalId
        L.pop(1)
        if (isSelf) 1 else 0
    } else 0

    fun find(
        L: LuaState,
        candidates: List<BoundFunction>,
        native: TwineNative
    ): BoundFunction {
        val offset = offset(L, native)

        val argCount = L.top() - 1 - offset

        val matching = candidates.filter { it.accepts(argCount) }

        return matching.firstOrNull { func ->
            LuaTypeChecker.matches(L, func, argCount, offset)
        } ?: throw TwineOverloadException(
            functionName = candidates.first().name,
            args = getArgs(L, argCount, offset),
            options = candidates
        )
    }

    private fun getArgs(
        L: LuaState,
        argCount: Int,
        offset: Int
    ): List<String> {
        return (0 until argCount).map { i ->
            val index = i + 2 + offset

            when {
                L.isNil(index) -> "nil"
                L.isBoolean(index) -> "boolean"
                L.isNumber(index) -> "number (${L.checkNumber(index)})"
                L.isString(index) -> "string (${L.checkString(index)})"
                L.isTable(index) -> "table"
                else -> L.type(index).toString().lowercase()
            }
        }
    }
}