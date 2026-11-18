package computer.obscure.twine.engine

import computer.obscure.twine.TwineError
import computer.obscure.twine.TwineNative
import computer.obscure.twine.natives.BoundFunction
import computer.obscure.twine.types.LuaTypeChecker
import net.hollowcube.luau.LuaState

object OverloadResolver {
    // if the first arg is self, its colon syntax and we drop self
    // because we already have it :)
    fun offset(L: LuaState, native: TwineNative) = if (L.isTable(2)) {
        L.getField(2, "__twineName")
        val isSelf = !L.isNil(-1) &&
                L.checkString(-1) == native.resolvedName
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
        } ?: throw TwineError("No matching overload found")
    }
}