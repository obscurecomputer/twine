package computer.obscure.twine

import net.hollowcube.luau.LuaFunc
import net.hollowcube.luau.LuaState
import net.hollowcube.luau.compiler.DebugLevel
import net.hollowcube.luau.compiler.LuauCompiler
import net.hollowcube.luau.compiler.OptimizationLevel
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1


class TwineEngine {
    val state: LuaState = LuaState.newState()
    val natives: MutableMap<String, TwineNative> = mutableMapOf()

    private val errorHandlers: MutableList<Pair<Regex, (MatchResult, String, String) -> String>> = mutableListOf()

    init {
        state.openLibs()
    }

    fun close() {
        natives.clear()
        state.close()
    }

    fun register(native: TwineNative) {
        pushNativeTable(state, native)
        state.setGlobal(native.resolvedName)
    }

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

    fun setBase(native: TwineNative) {
        val L = state
        L.getGlobal("_G")

        for ((name, func) in native.getFunctions()) {
            L.pushFunction(
                LuaFunc.wrap({ innerL: LuaState ->
                    val args = resolveArgs(innerL, func)
                    val result = func.call(native, *args)
                    val returnType = func.returnType.classifier as? KClass<*>
                    LuaTypeResolver.push(innerL, result, returnType) { l, n -> pushNativeTable(l, n) }
                    1
                }, func.name)
            )
            L.setField(-2, name)
        }

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

    fun run(script: String): Any? = runUnsafe("script.luau", script)

    fun runSafe(script: String) = runSafe("script.luau", script)

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

    private fun runUnsafe(name: String, script: String): Any? {
        val compiler = LuauCompiler.builder()
            .optimizationLevel(OptimizationLevel.BASELINE)
            .debugLevel(DebugLevel.NONE)
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
                    val func = OverloadResolver.find(callL, funcs, natives)
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

    private fun resolveArgs(L: LuaState, func: KFunction<*>): Array<Any?> {
        return Array(func.parameters.size - 1) { i ->
            val type = func.parameters[i + 1].type.classifier as? KClass<*>
            LuaTypeResolver.read(L, i + 1, type, natives)
        }
    }
}