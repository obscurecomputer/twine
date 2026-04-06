package computer.obscure.twine

import net.hollowcube.luau.LuaState
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

object OverloadResolver {

    fun find(L: LuaState, overloads: List<KFunction<*>>, natives: Map<String, TwineNative>): KFunction<*> {
        val argCount = L.top()
        return overloads.find { func -> matches(L, func, argCount, natives) }
            ?: error("No overload of '${overloads.first().name}' matches arguments")
    }

    private fun matches(L: LuaState, func: KFunction<*>, argCount: Int, natives: Map<String, TwineNative>): Boolean {
        val params = func.parameters.drop(1)
        val isVararg = params.lastOrNull()?.isVararg == true
        val fixedCount = if (isVararg) params.size - 1 else params.size

        if (argCount < fixedCount) return false
        if (!isVararg && argCount != fixedCount) return false

        return params.take(fixedCount).withIndex().all { (i, param) ->
            val type = param.type.classifier as? KClass<*>
            LuaTypeResolver.matches(L, i + 1, type, natives)
        }
    }
}