package computer.obscure.twine

import computer.obscure.twine.utils.getSignature
import net.hollowcube.luau.LuaState
import kotlin.reflect.KFunction
import kotlin.reflect.KType

/**
 * Utility for selecting the correct Kotlin function overload based on
 * the values currently present on the Luau stack.
 */
object OverloadResolver {

    /**
     * Examines the Luau stack and finds the first function in [overloads] that
     * matches the types and count of the provided arguments.
     *
     * @param L The current Luau state.
     * @param overloads A list of Kotlin functions with the same name.
     * @param natives A registry of known [TwineNative] objects for type checking.
     * @param funcName The name of the function.
     * @return The matching [KFunction].
     * @throws TwineError If no function signature matches the Luau arguments.
     */
    fun find(
        L: LuaState,
        overloads: List<KFunction<*>>,
        natives: Map<String, TwineNative>,
        funcName: String
    ): KFunction<*> {
        val argCount = L.top()

        if (overloads.size == 1) {
            val f = overloads[0]
            if (acceptsCount(f, argCount)) {
                firstMismatch(L, f, argCount, natives)?.let { (position, mismatch) ->
                    throw TwineError("bad argument #$position to '$funcName' ($mismatch)")
                }
                return f
            }
        }

        val match = overloads.find { func ->
            acceptsCount(func, argCount) && firstMismatch(L, func, argCount, natives) == null
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

    private fun acceptsCount(func: KFunction<*>, argCount: Int): Boolean {
        // drop(1) because the first parameter in reflection for
        // instance methods is usually just the instance itself ("this")
        val params = func.parameters.drop(1)
        val isVararg = params.lastOrNull()?.isVararg == true
        val fixedParamCount = if (isVararg) params.size - 1 else params.size

        val requiredCount = params.count { p ->
            !p.isVararg && !p.type.isMarkedNullable && !p.isOptional
        }

        return argCount >= requiredCount && (isVararg || argCount <= fixedParamCount)
    }

    private fun firstMismatch(
        L: LuaState,
        func: KFunction<*>,
        argCount: Int,
        natives: Map<String, TwineNative>
    ): Pair<Int, LuaTypeResolver.TypeMismatch>? {
        val params = func.parameters.drop(1)
        val isVararg = params.lastOrNull()?.isVararg == true
        val fixedParamCount = if (isVararg) params.size - 1 else params.size

        for (i in 0 until argCount) {
            val type: KType? = if (i < fixedParamCount) {
                params[i].type
            } else {
                params.last().type.arguments.firstOrNull()?.type
            }
            if (type == null) continue

            val mismatch = LuaTypeResolver.check(L, i + 1, type, natives)
            if (mismatch != null) return (i + 1) to mismatch
        }
        return null
    }
}
