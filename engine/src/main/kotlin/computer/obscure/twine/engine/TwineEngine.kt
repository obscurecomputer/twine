package computer.obscure.twine.engine

import computer.obscure.twine.TwineNative
import computer.obscure.twine.exceptions.TwineException
import computer.obscure.twine.natives.ClassModel
import computer.obscure.twine.natives.TwineEnvironment
import computer.obscure.twine.types.LuaValueReader
import computer.obscure.twine.types.LuaValueWriter
import net.hollowcube.luau.LuaFunc
import net.hollowcube.luau.LuaState
import net.hollowcube.luau.compiler.LuauCompiler
import net.hollowcube.luau.compiler.OptimizationLevel
import net.hollowcube.luau.internal.vm.`luaW_newuserdatadtor$dtor`
import net.hollowcube.luau.internal.vm.luaujava_h
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout


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
    private class RuntimeRef(val registry: TwineNativeRegistry, val key: String) {
        var count = 0
    }

    val state = LuaState.newState()
    val globalEnvironment = TwineEnvironment()
    val environments = mutableMapOf<String, TwineEnvironment>()
    private val runtimeRefs = HashMap<Long, RuntimeRef>()

    val lock = Any()

    @Volatile var closed = false
        private set

    fun close() = synchronized(lock) {
        if (closed) return
        closed = true
        state.close()
    }

    private val moduleRefs = HashMap<String, Int>()
    private val loadingModules = HashSet<String>()

    fun clearModules() = synchronized(lock) {
        moduleRefs.values.forEach { state.unref(it) }
        moduleRefs.clear()
    }

    init {
        state.openLibs()
    }

    private fun resolveNative(env: TwineEnvironment, key: String) =
        env.registry[key]
            ?: env.runtimeRegistry[key]
            ?: globalEnvironment.registry[key]
            ?: context.registry[key]

    private fun resolveClassModel(env: TwineEnvironment, key: String) =
        env.registry.getClassModel(key)
            ?: env.runtimeRegistry.getClassModel(key)
            ?: globalEnvironment.registry.getClassModel(key)
            ?: context.registry.getClassModel(key)

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

    val dtorStub: MemorySegment = `luaW_newuserdatadtor$dtor`.allocate(
        { ptr ->
            try {
                val id = ptr.reinterpret(8).get(ValueLayout.JAVA_LONG, 0)
                synchronized(lock) {
                    val ref = runtimeRefs[id] ?: return@synchronized
                    if (--ref.count <= 0) {
                        runtimeRefs.remove(id)
                        ref.registry.remove(ref.key)
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        },
        Arena.global()
    )

    fun handleIndex(
        L: LuaState,
        env: TwineEnvironment,
        native: TwineNative,
        classModel: ClassModel,
        id: String,
        key: String
    ): Int {
        val funcs = classModel.functions[key]
        if (funcs != null) {
            L.newTable()
            pushTwineInfo(L, id, native, env)

            L.pushString(key)
            L.setField(-2, "__twineFunc")

            L.newTable()
            L.pushFunction(SHARED_CALL_FUNC)
            L.setField(-2, "__call")
            L.setMetaTable(-2)
            L.pushString(key)
            L.pushValue(-2)
            L.rawSet(1)
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

    private fun compile(source: String, env: TwineEnvironment): ByteArray {
        val compiler = LuauCompiler.builder()
            .optimizationLevel(OptimizationLevel.MAXIMUM)
//            .debugLevel(DebugLevel.NONE)
            .mutableGlobals(
                (env.registry.all() + globalEnvironment.registry.all() + context.registry.all())
                    .toList().map { it.resolvedName }
            )
            .build()
        return compiler.compile(source)
    }

    fun run(
        script: String,
        env: TwineEnvironment = globalEnvironment,
        name: String = "main.luau"
    ) {
        val bytecode = compile(script, env)

        val thread = state.newThread()
        environments[env.uuid] = env
        thread.sandboxThread()
        pushNatives(thread, env)
        enableRequire(thread, env)

        thread.load(name, bytecode)
        thread.call(0, 0)

        state.pop(1)
    }

    private fun enableRequire(thread: LuaState, env: TwineEnvironment) {
        thread.pushFunction(LuaFunc.wrap({ L ->
            val name = L.checkString(1)

            moduleRefs[name]?.let { ref ->
                L.getRef(ref)
                return@wrap 1
            }

            if (!loadingModules.add(name)) {
                throw TwineException("Circular require of module '$name'")
            }

            try {
                val loader = env.moduleLoader ?: context.moduleLoader
                val source = loader?.invoke(name)
                    ?: throw TwineException("Module '$name' not found")

                val bytecode = try {
                    compile(source, env)
                } catch (e: Exception) {
                    throw TwineException("Failed to compile module '$name': ${e.message}")
                }

                L.load(name, bytecode)
                L.call(0, 1)

                if (L.isNil(-1)) {
                    L.pop(1)
                    L.pushBoolean(true)
                }

                moduleRefs[name] = L.ref(-1)
                1
            } finally {
                loadingModules.remove(name)
            }
        }, "require"))
        thread.setGlobal("require")
    }

    private fun pushNatives(thread: LuaState, env: TwineEnvironment) {
        for (registry in listOf(context.registry, globalEnvironment.registry, env.registry)) {
            registry.all().forEach {
                pushNative(thread, it, env)
                thread.setGlobal(it.resolvedName)
            }
        }
    }

    fun pushTwineInfo(L: LuaState, id: String, native: TwineNative, env: TwineEnvironment) {
        L.pushString(id)
        L.setField(-2, "__twineId")
        L.pushString(native.resolvedName)
        L.setField(-2, "__twineName")
        L.pushString(env.uuid)
        L.setField(-2, "__twineEnv")
    }

    fun pushNative(L: LuaState, native: TwineNative, env: TwineEnvironment) {
        val id = env.registry.findKey(native)
            ?: globalEnvironment.registry.findKey(native)
            ?: context.registry.findKey(native)
            ?: env.runtimeRegistry.findKey(native)
            ?: env.runtimeRegistry.addRuntime(native)

        if (id.startsWith("runtime:")) {
            synchronized(lock) {
                runtimeRefs
                    .getOrPut(native.internalIdLong!!) { RuntimeRef(env.runtimeRegistry, id) }
                    .count++
            }
        }

        L.newTable()
        pushTwineInfo(L, id, native, env)

        val block = luaujava_h.luaW_newuserdatadtor(L.L(), 8L, dtorStub).reinterpret(8)
        block.set(ValueLayout.JAVA_LONG, 0, native.internalIdLong!!)

        L.setField(-2, "__gcsentinel")

        L.getRef(metaRef)
        L.setMetaTable(-2)
    }

    private val metaRef: Int = run {
        state.newTable()

        state.pushFunction(SHARED_INDEX_FUNC)
        state.setField(-2, "__index")

        state.pushFunction(SHARED_NEWINDEX_FUNC)
        state.setField(-2, "__newindex")

        state.ref(-1).also {
            state.pop(1)
        }
    }
}