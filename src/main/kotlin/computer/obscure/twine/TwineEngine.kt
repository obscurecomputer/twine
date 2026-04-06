package computer.obscure.twine

import net.hollowcube.luau.LuaFunc
import net.hollowcube.luau.LuaState
import net.hollowcube.luau.compiler.DebugLevel
import net.hollowcube.luau.compiler.LuauCompiler
import net.hollowcube.luau.compiler.OptimizationLevel
import java.lang.ref.WeakReference
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
    var closed = false
        private set
    val stateLock = Any()

    val state: LuaState = LuaState.newState()

    /**
     * Global registry of Kotlin objects exposed to the Lua VM
     */
    val natives: MutableMap<String, TwineNative> = mutableMapOf()

    /**
     * Custom handlers to rewrite some error messages in a more intuitive form
     */
    private val errorHandlers: MutableList<Pair<Regex, (MatchResult, String, String) -> String>> = mutableListOf()

    var moduleLoader: ((name: String) -> String?)? = null

    init {
        // Load standard Luau libraries
        state.openLibs()

        state.newTable()
        state.setGlobal("__twine_callbacks")

        onError(Regex("""attempt to index \? \(a nil value\)""")) { _, scriptName, raw ->
            val line = raw.substringAfter(":").substringBefore(" ")
            "Lua error in $scriptName:$line — attempted to index a nil value"
        }
    }

    /**
     * Clean up resources and close the VM.
     */
    fun close() {
        closed = true
        natives.clear()
        state.close()
    }

    /**
     * Exposes a [TwineNative] instance as a global variable in the Lua environment.
     *
     * @param native The Kotlin object instance to expose to Lua.
     */
    fun register(native: TwineNative) {
        if (closed) return
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
        if (closed) return
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
        if (closed) return
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
        if (closed) {
            TwineLogger.error("Attempted to run script '$name' on a closed engine!")
            throw TwineError("Engine is closed")
        }
        if (script.trim().isEmpty()) return null

        val mainStackStart = state.top()

        val compiler = LuauCompiler.builder()
            .optimizationLevel(OptimizationLevel.NONE)
            .debugLevel(DebugLevel.NONE)
            // Tell the compiler that these names change and to not cache them
            .mutableGlobals(natives.keys.toList())
            .build()

        val bytecode = try {
            compiler.compile(script)
        } catch (e: Exception) {
            TwineLogger.error("Compilation failed for $name: ${e.message}")
            throw e
        }

        if (bytecode.isEmpty()) {
            TwineLogger.warn("Compiler returned empty bytecode for $name")
            return null
        }

        val thread = state.newThread()
        TwineLogger.debug("Thread created for $name [Parent Stack: $mainStackStart -> ${state.top()}]")

        // !! Sandbox the current thread !!
        thread.sandboxThread()

        try {
            // Load bytecode into the thread
            thread.load(name, bytecode)

            val stackBeforeExec = thread.top()
            val status = thread.resume(null, 0)

            if (status.ordinal > 1) {
                val error = if (thread.top() > 0) thread.checkString(-1) else "Unknown VM Error"
                TwineLogger.error("Runtime Error in $name: $error")
                throw RuntimeException(error)
            }

            val top = thread.top()
            if (top != 0) {
                val typeName = thread.typeName(top)
                TwineLogger.debug("Script $name finished. Thread Stack: $stackBeforeExec -> $top (Type: $typeName)")
            }

            if (top <= 0) return null

            if (thread.isTable(top)) {
                return try {
                    val map = tableToMap(thread, top)
                    TwineLogger.debug("Successfully marshaled table for $name")
                    map
                } catch (e: Exception) {
                    TwineLogger.error("Failed to marshal table for $name: ${e.message}")
                    "{Error Marshaling Table}"
                }
            }

            return try {
                LuaTypeResolver.read(thread, top, Any::class, natives)
            } catch (e: Exception) {
                TwineLogger.error("Failed to marshal return value for $name: ${e.message}")
                null
            }

        } finally {
            thread.top(0)

            state.top(mainStackStart)

            if (state.top() != mainStackStart) {
                TwineLogger.warn("Stack imbalance detected in $name! Expected $mainStackStart, got ${state.top()}")
            }
        }
    }

    /**
     * Recursively converts a Lua table into a Kotlin Map.
     * Also detects if a table is actually a Proxy or a Native object.
     */
    fun tableToMap(L: LuaState, index: Int): Any? {
        val absoluteIndex = if (index < 0) L.top() + index + 1 else index

        // Check if this table has the "__twineName" field
        L.getField(absoluteIndex, "__twineName")
        val isNative = !L.isNil(-1)
        // Clean the stack
        L.pop(1)

        if (isNative) {
            // If it's a Kotlin object, unwrap it
            return LuaTypeResolver.read(L, absoluteIndex, Any::class, natives)
        }

        val map = mutableMapOf<Any?, Any?>()

        // Starting point for L.next()
        L.pushNil()

        while (L.next(absoluteIndex)) {
            // Stack TOP (-1) = Value
            // Stack BELOW (-2) = Key
            val key = try {
                // If the key is a string or a number, convert
                if (L.isString(-2) || L.isNumber(-2)) {
                    LuaTypeResolver.read(L, -2, Any::class, natives)
                } else {
                    // If the key is strange, label it by its type
                    "__lua_key_" + L.typeName(-2)
                }
            } catch (_: Exception) { "unknown_key" }

            val value = when {
                // If the value is a table, recurse and convert it to a Map.
                L.isTable(-1) -> tableToMap(L, -1)
                // If Lua passed a function, capture it so Kotlin can call it later
                // as a LuaCallback.
                L.isFunction(-1) -> captureCallback(L, -1)

                // Handle other primitives
                else -> try {
                    LuaTypeResolver.read(L, -1, Any::class, natives)
                } catch (_: Exception) { "{Unreadable}" }
            }

            // Add the translated pair to the Kotlin map
            map[key] = value

            // !! Pop the value, but leave the key on the stack !!
            // !! L.next() needs the key to find the next entry in the table !!
            L.pop(1)
        }
        return map
    }

    /**
     * Pushes a proxy table onto the Luau stack that represents a Kotlin [TwineNative].
     * Uses metatables to intercept `__index` (get/call) and `__newindex` (set) operations.
     *
     * @param L The current [LuaState].
     * @param native The Kotlin object instance being proxied.
     */
    fun pushNativeTable(L: LuaState, native: TwineNative) {
        val tableName = native.resolvedName

        // Unique key to prevent native name collisions
        val instanceKey = "native@${System.identityHashCode(native)}"

        // Store the native in the registry so Kotlin doesn't delete it
        natives[instanceKey] = native

        L.newTable() // proxy table

        // Stamp the table so it can be recognized as a native table
        L.pushString(instanceKey)
        L.setField(-2, "__twineName")

        L.newTable() // metadata table

        // __index:
        // called when Lua tries to read a property or function
        // does *NOT* cache due to .mutableGlobals(natives.keys.toList())
        L.pushFunction(LuaFunc.wrap({ innerL ->
            val key = innerL.checkString(2)

            if (key == "__twineName") {
                innerL.pushString(tableName)
                return@wrap 1
            }

            // Functions check
            val functions = native.getFunctions().filter { it.first == key }
            if (functions.isNotEmpty()) {
                val funcs = functions.map { it.second }
                innerL.pushFunction(LuaFunc.wrap({ callL ->
                    val func = OverloadResolver.find(callL, funcs, natives, key)
                    val args = resolveArgs(callL, func)
                    val result = func.call(native, *args)
                    val returnType = func.returnType.classifier as? KClass<*>
                    LuaTypeResolver.push(callL, result, returnType) { l, n ->
                        pushNativeTable(l, n)
                    }
                    1
                }, key))
                return@wrap 1
            }

            // Props check
            val prop = native.getProperties().find { it.first == key }?.second
            if (prop != null) {
                @Suppress("UNCHECKED_CAST")
                val value = (prop as KProperty1<Any, *>).get(native)
                val returnType = prop.returnType.classifier as? KClass<*>
                LuaTypeResolver.push(innerL, value, returnType) { l, n ->
                    pushNativeTable(l, n)
                }
                return@wrap 1
            }

            innerL.pushNil()
            1
        }, "__index"))
        L.setField(-2, "__index")

        // __newindex:
        // called when Lua tries to set a property
        L.pushFunction(LuaFunc.wrap({ innerL ->
            val key = innerL.checkString(2)
            val prop = native.getProperties().find { it.first == key }?.second

            if (prop is kotlin.reflect.KMutableProperty1<*, *>) {
                val valueType = prop.returnType.classifier as? KClass<*>
                val newValue = LuaTypeResolver.read(innerL, 3, valueType, natives)
                @Suppress("UNCHECKED_CAST")
                (prop as kotlin.reflect.KMutableProperty1<Any, Any?>).set(native, newValue)

                return@wrap 0
            }

            innerL.rawSet(1)
            0
        }, "__newindex"))
        L.setField(-2, "__newindex")

        L.setMetaTable(-2)
    }

    /**
     * Enables the "require" module in the Luau VM.
     * * Mimics the standard Lua "require" behavior:
     * * Checks `package.loaded` cache to see if the script already ran
     * * If not, use a Kotlin callback to fetch the source code from a list of loaded scripts
     * * Compile, execute, and cache
     */
    fun enableRequire() {
        state.newTable() // package table
        state.newTable() // loaded sub-table
        state.setField(-2, "loaded") // package.loaded = {}
        state.setGlobal("package") // _G.package = package

        state.pushFunction(LuaFunc.wrap({ L ->
            // The first argument passed to require(string)
            val moduleName = L.checkString(1)

            // Look in package.loaded[moduleName]
            L.getGlobal("package")
            L.getField(-1, "loaded")
            L.getField(-1, moduleName)

            if (!L.isNil(-1)) {
                // !! Already loaded !!
                // Remove "package" and "loaded" tables from stack
                L.remove(-2)
                L.remove(-2)
                // Return the cached value
                return@wrap 1
            }
            // Not in cache, clean up nil and tables to clear the stack
            L.pop(3)

            // Fetch the source from the moduleLoader
            val source = moduleLoader?.invoke(moduleName)
                ?: throw TwineError("module '$moduleName' not found")

            val compiler = LuauCompiler.builder()
                .optimizationLevel(OptimizationLevel.BASELINE)
                .debugLevel(DebugLevel.NONE)
                .mutableGlobals(natives.keys.toList())
                .build()
            val bytecode = compiler.compile(source)

            L.load(moduleName, bytecode)

            // 1 = expects one return value, which is a module table here
            L.call(0, 1)

            // If the script returns nothing, default to true
            // (standard to indicate that the module loaded, but did not return anything)
            if (L.isNil(-1)) {
                L.pop(1)
                L.pushBoolean(true)
            }

            // Store the result in package.loaded[moduleName]
            L.getGlobal("package")
            L.getField(-1, "loaded")
            // Push a copy of the module result to the top
            L.pushValue(-3)
            L.setField(-2, moduleName)
            // Remove "package" and "loaded" tables
            L.pop(2)

            // Return the module result to the script that called require()
            1
        }, "require"))
        state.setGlobal("require")
    }

    /**
     * Marshals Luau stack values into a Kotlin-compatible array of arguments for function invocation.
     *
     * @param L The current [LuaState].
     * @param func The Kotlin function being called.
     * @return An array of Kotlin objects mapped from the Luau stack.
     */
    private fun resolveArgs(L: LuaState, func: KFunction<*>): Array<Any?> {
        // drop(1) because the first parameter of a class function is "this"
        val params = func.parameters.drop(1)
        // Pass a WeakReference of the engine so that if a parameter is a LuaCallback,
        // it can find its way to the engine and do table parsing.
        val engineRef = WeakReference(this)
        // Get the count of items on the stack
        val argCount = L.top()

        return Array(params.size) { i ->
            val param = params[i]
            val type = param.type.classifier as? KClass<*>

            if (param.isVararg) {
                // Find where the varargs start in the stack
                val varargStart = i + 1
                // Calculate how many extra arguments Lua provided for this vararg.
                val varargCount = (argCount - varargStart + 1).coerceAtLeast(0)

                // Get the component type (if vararg is String, component is String)
                val componentType = param.type.arguments.firstOrNull()?.type?.classifier as? KClass<*> ?: Any::class

                val array = java.lang.reflect.Array.newInstance(componentType.java, varargCount)
                for (j in 0 until varargCount) {
                    // Read the value from Lua and convert it to the Kotlin component type
                    val value = LuaTypeResolver.read(L, varargStart + j, componentType, natives, engineRef)
                    // Put the value in the array
                    java.lang.reflect.Array.set(array, j, value)
                }

                // Return the finished array as the argument for this parameter
                array
            } else {
                // If it is a standard parameter, just do a normal
                // 1:1 mapping
                LuaTypeResolver.read(L, i + 1, type, natives, engineRef)
            }
        }
    }
    /**
     * Captures a Lua function from the stack and anchors it into a [LuaCallback].
     *
     * @param L The current LuaState where the function lives.
     * @param index The stack position of the function to capture (-1 for the top).
     * @return A [LuaCallback] instance that acts as a permanent control for that function.
     */
    private fun captureCallback(L: LuaState, index: Int): LuaCallback {
        return LuaCallback.capture(L, index, WeakReference(this))
    }
}