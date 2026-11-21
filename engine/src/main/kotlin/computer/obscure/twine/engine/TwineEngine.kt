package computer.obscure.twine.engine

import computer.obscure.twine.exceptions.TwineException
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import computer.obscure.twine.natives.ClassModel
import computer.obscure.twine.natives.TwineEnvironment
import computer.obscure.twine.types.LuaValueReader
import computer.obscure.twine.types.LuaValueWriter
import net.hollowcube.luau.LuaFunc
import net.hollowcube.luau.LuaState
import net.hollowcube.luau.compiler.DebugLevel
import net.hollowcube.luau.compiler.LuauCompiler
import net.hollowcube.luau.compiler.OptimizationLevel

/**
 * The main entry point for the Twine Luau integration.
 * This class manages the Luau VM lifecycle, registration of Kotlin natives,
 * and script execution.
 *
 * Provides an intuitive engine abstracting most of the functionality of Twine.
 */

class TwineEngine(
    val context: TwineContext
) {
    val state = LuaState.newState()
    val globalEnvironment = TwineEnvironment()
    val environments = mutableMapOf<String, TwineEnvironment>()

    init {
        state.openLibs()
    }

    private fun resolveNative(env: TwineEnvironment, key: String) =
        env.registry[key] ?: env.runtimeRegistry[key]

    private fun resolveClassModel(env: TwineEnvironment, key: String) =
        env.registry.getClassModel(key) ?: env.runtimeRegistry.getClassModel(key)

    private val SHARED_INDEX_FUNC = LuaFunc.wrap({ L ->
        L.getField(1, "__twineId")
        val id = L.checkString(-1); L.pop(1)
        L.getField(1, "__twineEnv")
        val environment = L.checkString(-1); L.pop(1)

        val key = L.checkString(2)

        val env = environments[environment] ?: return@wrap 0
        val native = resolveNative(env, id) ?: return@wrap 0
        val classModel = resolveClassModel(env, id) ?: return@wrap 0

        handleIndex(L, env, native, classModel, id, key)
    }, "__index")

    private val SHARED_NEWINDEX_FUNC = LuaFunc.wrap({ L ->
        L.getField(1, "__twineId")
        val id = L.checkString(-1)
        L.pop(1)

        L.getField(1, "__twineEnv")
        val environment = L.checkString(-1)
        L.pop(1)

        val env = environments[environment] ?: return@wrap 0
        val native = resolveNative(env, id) ?: return@wrap 0
        val classModel = resolveClassModel(env, id) ?: return@wrap 0
        val key = L.checkString(2)

        handleNewIndex(L, env, native, classModel, key)
    }, "__newindex")

    private val SHARED_CALL_FUNC = LuaFunc.wrap({ L ->
        L.getField(1, "__twineId")
        val id = L.checkString(-1)
        L.pop(1)

        L.getField(1, "__twineEnv")
        val environment = L.checkString(-1)
        L.pop(1)

        L.getField(1, "__twineFunc")
        val key = L.checkString(-1)
        L.pop(1)

        val env = environments[environment] ?: return@wrap 0
        val native = resolveNative(env, id) ?: return@wrap 0
        val classModel = resolveClassModel(env, id) ?: return@wrap 0
        handleCall(L, env, native, classModel, key)
    }, "__call")

    fun handleIndex(
        L: LuaState,
        env: TwineEnvironment,
        native: TwineNative,
        classModel: ClassModel,
        id: String,
        key: String
    ): Int {
        val funcs = classModel.functions.filter { it.key == key }
        if (funcs.isNotEmpty()) {
            L.newTable()
            pushTwineInfo(L, id, key, env)

            L.pushString(key)
            L.setField(-2, "__twineFunc")

            L.newTable()
            L.pushFunction(SHARED_CALL_FUNC)
            L.setField(-2, "__call")
            L.setMetaTable(-2)
            return 1
        }

        val prop = classModel.properties[key]
        if (prop != null) {
            val value = prop.get(native)
//            println("GOT $value")
            LuaValueWriter.push(L, value, this, env)
            return 1
        }

        return 0
    }

    fun handleNewIndex(
        L: LuaState,
        env: TwineEnvironment,
        native: TwineNative,
        classModel: ClassModel,
        key: String
    ): Int {
        val prop = classModel.properties[key] ?: return 0
//        println("__NEWINDEX: ${L.checkString(2)} = ${L.toString(3)}")
        if (!prop.mutable) {
            throw TwineException("Property '$key' is read-only")
        }

        val value = LuaValueReader.read(
            L, 3, prop.klass, context, env
        )

//        println("BEFORE SET: ${prop.get(native)}")
//        println("SETTING: $value")
        prop.set(native, value)
//        println("AFTER SET: ${prop.get(native)}")

        return 0
    }

    fun handleCall(
        L: LuaState,
        env: TwineEnvironment,
        native: TwineNative,
        classModel: ClassModel,
        key: String
    ): Int {
        val candidates = classModel.functions[key] ?: return 0
//        println(candidates)
//        println(key)
//        println("CALLING FUNC WITH NAME $key, ${candidates.size} CANDIDATES!")
        val candidate = OverloadResolver.find(
            L,
            candidates = candidates,
            native
        )
        val offset = OverloadResolver.offset(L, native)
//        println("CANDIDATE: $candidate | $offset")
        val argCount = L.top() - 1 - offset
        val args = Array(candidate.params.size) { i ->
            val param = candidate.params[i]

            if (param.isVararg) {
                val start = i + 2 + offset
                val count = argCount - i

                Array(count) { j ->
                    LuaValueReader.read(
                        L,
                        start + j,
                        param.elementClass,
                        context,
                        env,
                        param.functionReturnClass
                    )
                }
            } else {
                LuaValueReader.read(
                    L,
                    i + 2 + offset,
                    param.klass,
                    context,
                    env,
                    param.functionReturnClass
                )
            }
        }

        val result = candidate.call(native, args)

        if (candidate.returnClass == Unit::class.java ||
            candidate.returnClass == Void.TYPE
        ) {
            return 0
        }
        LuaValueWriter.push(L, result, this, env)
        return 1
    }

    fun run(
        script: String,
        env: TwineEnvironment = globalEnvironment,
        name: String = "main.luau"
    ) {
        val compiler = LuauCompiler.builder()
            .optimizationLevel(OptimizationLevel.MAXIMUM)
//            .debugLevel(DebugLevel.NONE)
            .mutableGlobals(
                (env.registry.all() + globalEnvironment.registry.all())
                    .toList().map { it.resolvedName }
            )
            .build()
        val bytecode = compiler.compile(script)

        val thread = state.newThread()
        environments[env.uuid.toString()] = env
        thread.sandboxThread()
        pushNatives(thread, env)

        thread.load(name, bytecode)
        thread.call(0, 0)

        state.pop(1)
    }

    private fun pushNatives(thread: LuaState, env: TwineEnvironment) {
        env.registry.all().forEach {
            pushNative(thread, it, env)
            thread.setGlobal(it.resolvedName)
        }
    }

    fun pushTwineInfo(L: LuaState, id: String, name: String, env: TwineEnvironment) {
        L.pushString(id)
        L.setField(-2, "__twineId")
        L.pushString(name)
        L.setField(-2, "__twineName")

        L.pushString(env.uuid.toString())
        L.setField(-2, "__twineEnv")
    }

    fun pushNative(L: LuaState, native: TwineNative, env: TwineEnvironment) {
        val id = env.registry.findKey(native)
            ?: env.runtimeRegistry.findKey(native)
            ?: env.runtimeRegistry.addRuntime(native)

        L.newTable()
        pushTwineInfo(L, id, native.resolvedName, env)

        L.newTable()
        L.pushFunction(SHARED_INDEX_FUNC)
        L.setField(-2, "__index")
        L.pushFunction(SHARED_NEWINDEX_FUNC)
        L.setField(-2, "__newindex")
        L.setMetaTable(-2)
    }
}

fun main() {
    class Test : TwineNative("testobj") {
        @TwineProperty
        val test = "hi"

        @TwineFunction
        fun hello(test: String) {
            println("hello")
        }

        @TwineFunction
        fun hello(test: Int): String {
            println("hello integer")
            return "RETURN STRING!!"
        }

        @TwineFunction
        fun callback(test: (String) -> Boolean): Boolean {
            return test.invoke("test string")
        }
    }

    val env = TwineEnvironment()

    env.registry.add(Test())

    val context = TwineContext()
    context.engine.run(
        """
            print(
                testobj.callback(function(str)
                    if str == "test string" then
                        return true
                    end
                  
                    return false
                end)
            )
        """.trimIndent(),
        env
    )
}