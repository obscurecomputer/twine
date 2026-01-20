package computer.obscure.twine.nativex

import computer.obscure.twine.TwineError
import computer.obscure.twine.annotations.TwineNativeFunction
import computer.obscure.twine.annotations.TwineOverload
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
        fun getFunctions(obj: Any): Map<String, KFunction<*>> {
            return obj::class.functions
                .mapNotNull { func ->
                    func.findAnnotation<TwineNativeFunction>()?.let {annotation ->
                        val customName = annotation.name.takeIf { it != TwineNative.INHERIT_TAG } ?: func.name
                        customName to func
                    }
                }.toMap()
        }
    }

    fun register() {
        registerFunctions()
        registerOverloads()
    }

    /**
     * Registers functions annotated with {@code TwineNativeFunction} into the Lua table.
     */
    fun registerFunctions() {
        val functions = getFunctions(owner)
        functions.forEach { function ->
            val name = function.key
            val rawFunction = function.value

            owner.table.set(name, object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    return try {
                        val kotlinArgs = args.toKotlinArgs(rawFunction)
                        val result = rawFunction.call(owner, *kotlinArgs)
                        result.toLuaValue()
                    } catch (e: InvocationTargetException) {
                        ErrorHandler.throwError(e, rawFunction)
                    } as Varargs
                }
            })
        }
    }

    /**
     * Handles overloaded functions.
     */
    fun registerOverloads() {
        val functionMap = mutableMapOf<String, MutableList<KFunction<*>>>()

        val functions = getFunctions(owner)
        functions.forEach { function ->
            val name = function.key
            val rawFunction = function.value

            rawFunction.findAnnotation<TwineOverload>()
                ?: return@forEach

            functionMap.computeIfAbsent(name) {
                mutableListOf() }.add(rawFunction)
        }

        // Find a match depending on the arg count and the arg types
        functionMap.forEach { (name, overloadedFunctions) ->
            if (overloadedFunctions.isEmpty())
                throw TwineError("No overloaded functions found for $name")

            val overloadedFunction = object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    val argCount = args.narg()
                    val matchingFunction = overloadedFunctions.find { function ->
                        val params = function.parameters.drop(1) // Skip `this`
                        val isVararg = params.lastOrNull()?.isVararg == true
                        val fixedParamCount = if (isVararg) params.size - 1 else params.size

                        if (argCount < fixedParamCount) return@find false
                        if (!isVararg && argCount != fixedParamCount) return@find false

                        for (i in 0 until argCount) {
                            val paramType = params[i].type
                            val argType = args.arg(i + 1).toKotlinType()

                            if (!paramType.isSupertypeOf(argType)) {
                                return@find false
                            }
                        }
                        true
                    }

                    if (matchingFunction != null) {
                        val kotlinArgs = args.toKotlinArgs(matchingFunction)
                        return try {
                            val result = matchingFunction.call(owner, *kotlinArgs)
                            result.toLuaValue()
                        } catch (e: InvocationTargetException) {
                            ErrorHandler.throwError(e, matchingFunction)
                        } as Varargs
                    } else {
                        if (overloadedFunctions.isNotEmpty()) {
                            val firstParam = overloadedFunctions.first().parameters.getOrNull(1)

                            if (firstParam?.isVararg == true) {
                                val varargFunction = overloadedFunctions.find {
                                    val ps = it.parameters.drop(1)
                                    ps.size == 1 && ps[0].isVararg
                                } ?: throw TwineError("No vararg function found")

                                val varargArgs = args.toKotlinArgs(varargFunction)

                                return try {
                                    val result = varargFunction.call(owner, *varargArgs)
                                    result.toLuaValue()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    ErrorHandler.throwError(e, varargFunction)
                                } as Varargs
                            }
                        } else {
                            throw TwineError("No matching function found for $name with $argCount arguments")
                        }
                    }

                    return NIL
                }
            }
            owner.table.set(name, overloadedFunction)
        }
    }
}