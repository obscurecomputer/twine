package computer.obscure.twine

import computer.obscure.twine.utils.getSignature
import net.hollowcube.luau.LuaState
import net.hollowcube.luau.LuaType
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
     * @param funcName The name of the function.
     * @return The matching [KFunction].
     * @throws IllegalStateException If no function signature matches the Lua arguments.
     */
    fun find(
        L: LuaState,
        overloads: List<KFunction<*>>,
        natives: Map<String, TwineNative>,
        funcName: String
    ): KFunction<*> {
        val argCount = L.top()

        val match = overloads.find { func ->
            val params = func.parameters.drop(1)
            val isVararg = params.lastOrNull()?.isVararg == true
            val fixedParamCount = if (isVararg) params.size - 1 else params.size

            // Not enough arguments
            if (argCount < fixedParamCount) return@find false
            // Too many arguments (and no vararg to catch them)
            if (!isVararg && argCount != fixedParamCount) return@find false

            // Check types for each argument
            for (i in 0 until argCount) {
                // Skip type checking for varargs here or handle specifically
                if (i >= fixedParamCount && isVararg) break

                val paramType = params[i].type.classifier as? KClass<*>

                val isMatch = LuaTypeResolver.matches(L, i + 1, paramType, natives) ||
                        (isNumeric(paramType) && L.type(i + 1) == LuaType.NUMBER)

                if (!isMatch) return@find false
            }
            true
        }

        if (match != null) return match

        val passedArgs = if (argCount == 0) "none" else (1..argCount).joinToString(", ") { i ->
            L.type(i).name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        }

        val attempt = "$funcName($passedArgs)"
        val options = overloads.joinToString("\n") { " - ${it.name}${it.getSignature()}" }

        throw TwineError(
            "No matching overload found for: $attempt\n" +
                    "Available overloads for \"$funcName\":\n$options"
        )
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

    private fun isNumeric(type: KClass<*>?): Boolean {
        return type == Int::class || type == Double::class || type == Float::class || type == Long::class || type == Byte::class || type == Short::class
    }
}