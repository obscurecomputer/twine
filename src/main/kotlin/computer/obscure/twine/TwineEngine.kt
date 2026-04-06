package computer.obscure.twine

import net.hollowcube.luau.LuaFunc
import net.hollowcube.luau.LuaState
import net.hollowcube.luau.compiler.DebugLevel
import net.hollowcube.luau.compiler.LuauCompiler
import net.hollowcube.luau.compiler.OptimizationLevel
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1

/**
 * The main entry point for the Twine Luau integration.
 * This class manages the Luau VM lifecycle, registration of Kotlin natives,
 * and script execution.
 *
 * Provides an intuitive engine abstracting most of the functionality of Twine.
 */
class TwineEngine {
    val state: LuaState = LuaState.newState()

    /**
     *  Global registry of Kotlin objects exposed to the Lua VM */
    val natives: MutableMap<String, TwineNative> = mutableMapOf()

    /**
     * Custom handlers to rewrite some error messages in a more intuitive form
     */
    private val errorHandlers: MutableList<Pair<Regex, (MatchResult, String, String) -> String>> = mutableListOf()

    init {
        // Load standard Luau libraries
        state.openLibs()

        onError(Regex("""attempt to index \? \(a nil value\)""")) { _, scriptName, raw ->
            val line = raw.substringAfter(":").substringBefore(" ")
            "Lua error in $scriptName:$line — attempted to index a nil value"
        }
    }

    /**
     * Clean up resources and close the VM.
     */
    fun close() {
        natives.clear()
        state.close()
    }

    /**
     * Exposes a [TwineNative] instance as a global variable in the Lua environment.
     *
     * @param native The Kotlin object instance to expose to Lua.
     */
    fun register(native: TwineNative) {
        pushNativeTable(state, native)
        state.setGlobal(native.resolvedName)
    }

    /**
     * Registers a [TwineEnum], creating a global table in Lua containing
     * all enum constants as nested tables.
     *
     * @param enum The Twine representation of a Kotlin Enum.
     */
    fun register(enum: TwineEnum) {
        val values = enum.getValues()

        values.forEach { natives[it.resolvedName] = it }

        state.newTable()
        state.pushString(enum.resolvedName)
        state.setField(-2, "__twineName")

        values.forEach { value ->
            pushNativeTable(state, value)
            state.setField(-2, value.enumName)
        }

        state.setGlobal(enum.resolvedName)
    }

    /**
     * Injects functions and properties from a [TwineNative] directly into
     * the global Luau scope (_G).
     *
     * @param native The native object whose functions and properties will be injected into the global scope.
     */
    fun setBase(native: TwineNative) {
        val L = state
        L.getGlobal("_G")

        // Map Kotlin functions to global Lua functions
        val functions = native.getFunctions().groupBy({ it.first }, { it.second })

        for ((name, funcs) in functions) {
            L.pushFunction(
                LuaFunc.wrap({ innerL: LuaState ->
                    val func = OverloadResolver.find(innerL, funcs, natives, name)
                    val args = resolveArgs(innerL, func)
                    val result = func.call(native, *args)
                    val returnType = func.returnType.classifier as? KClass<*>
                    LuaTypeResolver.push(innerL, result, returnType) { l, n -> pushNativeTable(l, n) }
                    1
                }, name)
            )
            L.setField(-2, name)
        }

        // Map Kotlin properties to the global table via metatable
        val properties = native.getProperties()
        if (properties.isNotEmpty()) {
            L.newTable()

            L.pushFunction(LuaFunc.wrap({ innerL ->
                val key = innerL.checkString(2)
                val prop = properties.find { it.first == key }
                if (prop != null) {
                    @Suppress("UNCHECKED_CAST")
                    val value = (prop.second as KProperty1<Any, *>).get(native)
                    val returnType = prop.second.returnType.classifier as? KClass<*>
                    LuaTypeResolver.push(innerL, value, returnType) { l, n -> pushNativeTable(l, n) }
                    1
                } else {
                    innerL.pushNil()
                    1
                }
            }, "__index"))
            L.setField(-2, "__index")

            L.setMetaTable(-2)
        }

        L.pop(1)
    }

    fun onError(pattern: Regex, handler: (match: MatchResult, scriptName: String, raw: String) -> String) {
        errorHandlers.add(pattern to handler)
    }

    /**
     * Compiles and executes a Luau script string.
     *
     * @param script The Luau source code to execute.
     * @return The value returned by the script, if any, converted to a Kotlin type.
     */
    fun run(script: String): Any? = runUnsafe("script.luau", script)

    /**
     * Compiles and executes a Luau script string with default naming.
     *
     * @param script The Luau source code to execute.
     * @return A [Result] containing the return value or a [TwineError].
     */
    fun runSafe(script: String) = runSafe("script.luau", script)

    /**
     * Executes a script safely, catching Luau VM exceptions and passing them through
     * registered [errorHandlers].
     *
     * @param name The name of the script (used for stack traces).
     * @param script The Luau source code to execute.
     * @return A [Result] wrapping the script output/formatted error message.
     */
    fun runSafe(name: String, script: String): Result<Any?> {
        return try {
            Result.success(runUnsafe(name, script))
        } catch (e: Exception) {
            val rawMessage = e.message ?: "Unknown error"
            val newMessage = errorHandlers
                .firstOrNull { (regex, _) -> regex.containsMatchIn(rawMessage) }
                ?.let { (regex, handler) -> handler(regex.find(rawMessage)!!, name, rawMessage) }
                ?: rawMessage
            Result.failure(TwineError(newMessage))
        }
    }

    /**
     * Internal logic to compile and execute bytecode.
     * Configures the compiler to treat registered natives as mutable globals to prevent caching issues.
     *
     * @param name Script name for the VM.
     * @param script Source code.
     * @return The final value on the Luau stack after execution.
     */
    private fun runUnsafe(name: String, script: String): Any? {
        val compiler = LuauCompiler.builder()
            .optimizationLevel(OptimizationLevel.BASELINE)
            .debugLevel(DebugLevel.NONE)
            // IMPORTANT!!
            .mutableGlobals(natives.keys.toList())
            .build()
        val bytecode = compiler.compile(script)

        val thread = state.newThread()
        try {
            thread.sandboxThread()
            thread.load(name, bytecode)

            val status = thread.resume(null, 0)
            if (status.ordinal > 1) {
                throw RuntimeException(thread.checkString(-1))
            }

            val top = thread.top()
            return if (top > 0) {
                LuaTypeResolver.read(thread, top, Any::class, natives)
            } else null
        } finally {
            state.top(0)
        }
    }

    /**
     * Pushes a proxy table onto the Luau stack that represents a Kotlin [TwineNative].
     * Uses metatables to intercept `__index` (get/call) and `__newindex` (set) operations.
     *
     * @param L The current [LuaState].
     * @param native The Kotlin object instance being proxied.
     */
    private fun pushNativeTable(L: LuaState, native: TwineNative) {
        val tableName = native.resolvedName
        natives[tableName] = native

        L.newTable() // proxy table
        L.newTable()

        L.pushFunction(LuaFunc.wrap({ innerL ->
            val key = innerL.checkString(2)

            if (key == "__twineName") {
                innerL.pushString(tableName)
                return@wrap 1
            }

            val functions = native.getFunctions().filter { it.first == key }
            if (functions.isNotEmpty()) {
                val funcs = functions.map { it.second }
                innerL.pushFunction(LuaFunc.wrap({ callL ->
                    val func = OverloadResolver.find(callL, funcs, natives, key)
                    val args = resolveArgs(callL, func)
                    val result = func.call(native, *args)
                    val returnType = func.returnType.classifier as? KClass<*>
                    LuaTypeResolver.push(callL, result, returnType) { l, n -> pushNativeTable(l, n) }
                    1
                }, key))
                return@wrap 1
            }

            val prop = native.getProperties().find { it.first == key }?.second
            if (prop != null) {
                @Suppress("UNCHECKED_CAST")
                val value = (prop as KProperty1<Any, *>).get(native)
                val returnType = prop.returnType.classifier as? KClass<*>
                LuaTypeResolver.push(innerL, value, returnType) { l, n -> pushNativeTable(l, n) }
                return@wrap 1
            }

            innerL.pushNil()
            1
        }, "__index"))
        L.setField(-2, "__index")

        L.pushFunction(LuaFunc.wrap({ innerL ->
            val key = innerL.checkString(2)
            val prop = native.getProperties().find { it.first == key }?.second

            if (prop is kotlin.reflect.KMutableProperty1<*, *>) {
                val valueType = prop.returnType.classifier as? KClass<*>
                val newValue = LuaTypeResolver.read(innerL, 3, valueType, natives)
                @Suppress("UNCHECKED_CAST")
                (prop as kotlin.reflect.KMutableProperty1<Any, Any?>).set(native, newValue)

                println("$key > $newValue")
                return@wrap 0
            }

            innerL.rawSet(1)
            0
        }, "__newindex"))
        L.setField(-2, "__newindex")

        L.setMetaTable(-2)
    }

    /**
     * Marshals Luau stack values into a Kotlin-compatible array of arguments for function invocation.
     *
     * @param L The current [LuaState].
     * @param func The Kotlin function being called.
     * @return An array of Kotlin objects mapped from the Luau stack.
     */
    private fun resolveArgs(L: LuaState, func: KFunction<*>): Array<Any?> {
        val params = func.parameters.drop(1)
        val argCount = L.top()

        return Array(params.size) { i ->
            val param = params[i]
            val type = param.type.classifier as? KClass<*>

            if (param.isVararg) {
                val varargStart = i + 1
                val varargCount = (argCount - varargStart + 1).coerceAtLeast(0)

                // Get the component type (if vararg is String, component is String)
                val componentType = param.type.arguments.firstOrNull()?.type?.classifier as? KClass<*> ?: Any::class

                val array = java.lang.reflect.Array.newInstance(componentType.java, varargCount)
                for (j in 0 until varargCount) {
                    val value = LuaTypeResolver.read(L, varargStart + j, componentType, natives)
                    java.lang.reflect.Array.set(array, j, value)
                }
                array
            } else {
                LuaTypeResolver.read(L, i + 1, type, natives)
            }
        }
    }
}