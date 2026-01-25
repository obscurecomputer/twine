package computer.obscure.twine.nativex

import computer.obscure.twine.TwineError
import computer.obscure.twine.TwineLogger
import computer.obscure.twine.annotations.TwineNativeFunction
import computer.obscure.twine.annotations.TwineOverload
import computer.obscure.twine.nativex.conversion.Converter.getSignature
import computer.obscure.twine.nativex.conversion.Converter.toKotlinArgs
import computer.obscure.twine.nativex.conversion.Converter.toKotlinType
import computer.obscure.twine.nativex.conversion.Converter.toLuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSupertypeOf

class FunctionRegistrar(private val owner: TwineNative) {

    companion object {
        fun getFunctions(obj: Any): List<Pair<String, KFunction<*>>> {
            return obj::class.functions
                .mapNotNull { func ->
                    func.findAnnotation<TwineNativeFunction>()?.let { annotation ->
                        val customName = annotation.name.takeIf { it != TwineNative.INHERIT_TAG } ?: func.name
                        customName to func
                    }
                } // Removed .toMap()
        }
    }

    fun register() {
        val allFunctions = getFunctions(owner)
        val grouped = allFunctions.groupBy({ it.first }, { it.second })

        grouped.forEach { (name, funcs) ->
            if (funcs.size > 1 || funcs.any { it.findAnnotation<TwineOverload>() != null }) {
                registerOverloadedGroup(name, funcs)
            } else {
                registerSimpleFunction(name, funcs.first())
            }
        }
    }

    /**
     * Registers functions annotated with {@code TwineNativeFunction} into the Lua table.
     */
    private fun registerSimpleFunction(name: String, rawFunction: KFunction<*>) {
        owner.table.set(name, object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                return try {
                    val isColonCall = args.narg() > 0 && args.arg1() == owner.table
                    val kotlinArgs = args.toKotlinArgs(rawFunction, isColonCall)
                    val result = rawFunction.call(owner, *kotlinArgs)

                    handleResult(result)
                } catch (e: InvocationTargetException) {
                    ErrorHandler.throwError(e, rawFunction)
                } as Varargs
            }
        })
    }

    private fun handleResult(result: Any?): Varargs {
        return if (result == owner || result?.javaClass == owner.javaClass) {
            owner.table
        } else {
            result.toLuaValue()
        } as Varargs
    }

    /**
     * Handles overloaded functions.
     */
    fun registerOverloadedGroup(name: String, overloadedFunctions: List<KFunction<*>>) {
        owner.table.set(name, object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val argCount = args.narg()

                // try to find a direct match
                val matchingFunction = overloadedFunctions.find { function ->
                    val params = function.parameters.drop(1)
                    val isVararg = params.lastOrNull()?.isVararg == true
                    val fixedParamCount = if (isVararg) params.size - 1 else params.size

                    if (argCount < fixedParamCount) return@find false
                    if (!isVararg && argCount != fixedParamCount) return@find false

                    for (i in 0 until argCount) {
                        val paramType = params[i].type
                        val luaValue = args.arg(i + 1)
                        val argType = luaValue.toKotlinType()

                        TwineLogger.debug("Attempting to match arg $paramType/$argType to ${function.getSignature()}")

                        if (paramType == argType) {
                            TwineLogger.debug("Matched $paramType/$argType to ${function.getSignature()}")
                            continue
                        }
                        if (paramType.isSupertypeOf(argType)) {
                            TwineLogger.debug("Matched $argType as subtype of $paramType")
                            continue
                        }

                        TwineLogger.debug("Failed to match $paramType/$argType to ${function.getSignature()}")
                        return@find false
                    }
                    true
                }

                if (matchingFunction != null) {
                    return try {
                        TwineLogger.debug("Found matching overload: ${matchingFunction.getSignature()}")
                        val kotlinArgs = args.toKotlinArgs(matchingFunction)
                        val result = matchingFunction.call(owner, *kotlinArgs)
                        handleResult(result)
                    } catch (e: InvocationTargetException) {
                        ErrorHandler.throwError(e, matchingFunction)
                    } as Varargs
                }

                // vararg fallback
                val varargFunction = overloadedFunctions.find { it.parameters.drop(1).lastOrNull()?.isVararg == true }
                if (varargFunction != null) {
                    return try {
                        val varargArgs = args.toKotlinArgs(varargFunction)
                        val result = varargFunction.call(owner, *varargArgs)
                        handleResult(result)
                    } catch (e: Exception) {
                        ErrorHandler.throwError(e, varargFunction)
                    } as Varargs
                }

                throw TwineError("No matching overload found for '$name' with $argCount arguments.")
            }
        })
    }
}