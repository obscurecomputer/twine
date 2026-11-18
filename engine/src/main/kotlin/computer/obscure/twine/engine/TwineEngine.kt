package computer.obscure.twine.engine

import computer.obscure.twine.TwineError
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import computer.obscure.twine.natives.ClassModel
import computer.obscure.twine.natives.TwineEnvironment
import computer.obscure.twine.types.LuaValueReader
import computer.obscure.twine.types.LuaValueWriter
import net.hollowcube.luau.LuaFunc
import net.hollowcube.luau.LuaState
import net.hollowcube.luau.compiler.LuauCompiler

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

    private fun resolveNative(name: String, env: TwineEnvironment = globalEnvironment): TwineNative {
        return env.registry[name]
            ?: globalEnvironment.registry[name]
            ?: context.registry[name]
            ?: throw TwineError("Unknown native '$name'")
    }

    private val SHARED_INDEX_FUNC = LuaFunc.wrap({ L ->
//        val getTime = System.nanoTime()
        L.getField(1, "__twineName")
        val instanceKey = L.checkString(-1); L.pop(1)
        L.getField(1, "__twineEnv")
        val environment = L.checkString(-1); L.pop(1)

        val key = L.checkString(2)

//        println("got instance, env, key in ${System.nanoTime() - getTime}nanos")
//
//        val getEnvTime = System.nanoTime()
        val env = environments[environment] ?: return@wrap 0
//        println("got env from engine ${System.nanoTime() - getEnvTime}nanos")
//
//        val getNativeTime = System.nanoTime()
        val native = env.registry[instanceKey] ?: return@wrap 0
//        println("got native from env ${System.nanoTime() - getNativeTime}nanos")
//
//        val getCMTime = System.nanoTime()
        val classModel = env.registry.getClassModel(instanceKey) ?: return@wrap 0
//        println("got cm from env ${System.nanoTime() - getCMTime}nanos")

        handleIndex(L, env, native, classModel, key)
    }, "__index")

    private val SHARED_NEWINDEX_FUNC = LuaFunc.wrap({ L ->
        L.getField(1, "__twineName")
        val instanceKey = L.checkString(-1)
        L.pop(1)

        L.getField(1, "__twineEnv")
        val environment = L.checkString(-1)
        L.pop(1)

        val env = environments[environment] ?: return@wrap 0
        val native = env.registry[instanceKey] ?: return@wrap 0
        val classModel = env.registry.getClassModel(instanceKey) ?: return@wrap 0

        handleNewIndex(L, native, classModel)
    }, "__newindex")

    private val SHARED_CALL_FUNC = LuaFunc.wrap({ L ->
        L.getField(1, "__twineName")
        val instanceKey = L.checkString(-1)
        L.pop(1)

        L.getField(1, "__twineEnv")
        val environment = L.checkString(-1)
        L.pop(1)

        L.getField(1, "__twineFunc")
        val key = L.checkString(-1)
        L.pop(1)

        val env = environments[environment] ?: return@wrap 0
        val native = env.registry[instanceKey] ?: return@wrap 0
        val classModel = env.registry.getClassModel(instanceKey) ?: return@wrap 0

        handleCall(L, env, native, classModel, key)
    }, "__call")

    fun handleIndex(
        L: LuaState,
        env: TwineEnvironment,
        native: TwineNative,
        classModel: ClassModel,
        key: String
    ): Int {
        val funcs = classModel.functions.filter { it.key == key }
        if (funcs.isNotEmpty()) {
            L.newTable()
            pushTwineInfo(L, native, env)
            L.pushString(key)
            L.setField(-2, "__twineFunc")

            L.newTable()
            L.pushFunction(SHARED_CALL_FUNC)
            L.setField(-2, "__call")
            L.setMetaTable(-2)
            return 1
        }

        val prop = classModel.propertyList.find { it.name == key }
        if (prop != null) {
            val value = prop.get(native)
            LuaValueWriter.push(
                L,
                value,
                this,
                env
            )
            return 1
        }
        return 0
    }

    fun handleNewIndex(
        L: LuaState,
        native: TwineNative,
        classModel: ClassModel
    ): Int {
        val key = L.checkString(2)
        val prop = classModel.properties[key]
            ?: throw TwineError("Unknown property '$key'")

        if (!prop.mutable) {
            throw TwineError("Property '$key' is read-only")
        }

        val value = LuaValueReader.read(
            L,
            3,
            prop.klass,
            context,
            globalEnvironment
        )

        prop.set(native, value)
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
        val args = Array(argCount) { i ->
            val param = candidate.params[i]
            LuaValueReader.read(
                L,
                i + 2 + offset,
                param.klass,
                context,
                env
            )
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
        val bytecode = LuauCompiler.DEFAULT.compile(script)

        val thread = state.newThread()
        environments[env.uuid.toString()] = env
        pushNatives(env)
        thread.sandboxThread()

        thread.load(name, bytecode)
        thread.call(0, 0)

        state.pop(1)
    }

    private fun pushNatives(env: TwineEnvironment) {
        env.registry.all().forEach {
            pushNative(state, it, env)
            state.setGlobal(it.resolvedName)
        }
    }

    fun pushTwineInfo(L: LuaState, native: TwineNative, env: TwineEnvironment) {
        L.pushString(native.resolvedName)
        L.setField(-2, "__twineName")
        L.pushString(env.uuid.toString())
        L.setField(-2, "__twineEnv")
    }

    fun pushNative(L: LuaState, native: TwineNative, env: TwineEnvironment) {
        L.newTable()
        pushTwineInfo(L, native, env)

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
    }

    val env = TwineEnvironment()

    env.registry.add(Test())

    val context = TwineContext()
    context.engine.run(
        """
            print(testobj.hello("hiahahah"))
            print(testobj.hello(67))
            print(testobj.hello(testobj, 67))
            print(testobj:hello(67))
        """.trimIndent(),
        env
    )
}