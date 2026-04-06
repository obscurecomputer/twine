package computer.obscure.twine

import net.hollowcube.luau.LuaState
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * Utility for selecting the correct Kotlin function overload based on
 * the values currently present on the Luau stack.
 */
object OverloadResolver {

    /**
     * Examines the Lua stack and finds the first function in [overloads] that
     * matches the types and count of the provided arguments.
     *
     * @param L The current Lua state.
     * @param overloads A list of Kotlin functions with the same name.
     * @param natives A registry of known [TwineNative] objects for type checking.
     * @return The matching [KFunction].
     * @throws IllegalStateException If no function signature matches the Lua arguments.
     */
    fun find(L: LuaState, overloads: List<KFunction<*>>, natives: Map<String, TwineNative>): KFunction<*> {
        val argCount = L.top()
        // TODO: add back descriptive message from V1
        return overloads.find { func -> matches(L, func, argCount, natives) }
            ?: error("No overload of '${overloads.first().name}' matches arguments")
    }

    /**
     * Internal logic to check if a specific [KFunction] is compatible with the Lua stack.
     *
     * @param L The current [LuaState].
     * @param func The Kotlin function to test against.
     * @param argCount The number of arguments passed from Luau in the current call.
     * @param natives The registry of known [TwineNative] objects for type validation.
     * @return `true` if the Luau arguments can be safely converted into the Kotlin parameter types.
     */
    private fun matches(L: LuaState, func: KFunction<*>, argCount: Int, natives: Map<String, TwineNative>): Boolean {
        // drop(1) because the first parameter in reflection for
        // instance methods is usually just the instance itself ("this")
        val params = func.parameters.drop(1)
        val isVararg = params.lastOrNull()?.isVararg == true
        val fixedCount = if (isVararg) params.size - 1 else params.size

        // Not enough arguments
        if (argCount < fixedCount) return false
        // Too many arguments (and no vararg to catch them)
        if (!isVararg && argCount != fixedCount) return false

        return params.take(fixedCount).withIndex().all { (i, param) ->
            val type = param.type.classifier as? KClass<*>

            // Reuse LuaTypeResolver to see if the stack value at the index can be
            // marshalled into the Kotlin type.
            LuaTypeResolver.matches(L, i + 1, type, natives)
        }
    }
}