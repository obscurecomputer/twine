
package computer.obscure.twine.types

import computer.obscure.twine.TwineNative
import computer.obscure.twine.engine.TwineContext
import computer.obscure.twine.exceptions.TwineException
import computer.obscure.twine.natives.TwineEnvironment
import net.hollowcube.luau.LuaState

object LuaValueReader {
    fun read(
        state: LuaState,
        index: Int,
        type: Class<*>?,
        context: TwineContext,
        environment: TwineEnvironment,
        functionReturnClass: Class<*>? = null
    ): Any? {
        if (state.isNil(index)) return null
        if (type == null)
            return readAny(state, index, context, environment)

        if (Function::class.java.isAssignableFrom(type)) {
            return readFunction(
                state, index, type,
                functionReturnClass ?: Any::class.java,
                context, environment
            )
        }

        return when (type) {
            String::class.java -> state.checkString(index)
            Int::class.javaObjectType -> state.checkInteger(index)
            Long::class.javaObjectType -> state.checkNumber(index).toLong()
            Short::class.javaObjectType -> state.checkNumber(index).toInt().toShort()
            Byte::class.javaObjectType -> state.checkNumber(index).toInt().toByte()
            Double::class.javaObjectType -> state.checkNumber(index)
            Float::class.javaObjectType -> state.checkNumber(index).toFloat()
            Boolean::class.javaObjectType -> state.checkBoolean(index)

            else -> when {
                type.let { TwineNative::class.java.isAssignableFrom(it) } ->
                    readNative(state, index, context, environment)

                type.isEnum ->
                    readEnum(state, index, type)

                Map::class.java.isAssignableFrom(type) ->
                    readTable(state, index, context, environment)

                type.isInterface && isNativeTable(state, index) ->
                    readNative(state, index, context, environment)

                else -> readAny(state, index, context, environment)
            }
        }
    }

    fun readTable(
        state: LuaState,
        index: Int,
        context: TwineContext,
        environment: TwineEnvironment
    ): Map<Any?, Any?> {
        val result = mutableMapOf<Any?, Any?>()
        val tableIndex = state.absIndex(index)

        state.pushNil()

        while (state.next(tableIndex)) {
            val key = readAny(state, -2, context, environment)
            val value = readAny(state, -1, context, environment)

            result[key] = value
            state.pop(1)
        }

        return result
    }

    private fun readNative(
        state: LuaState,
        index: Int,
        context: TwineContext,
        environment: TwineEnvironment
    ): TwineNative {
        state.getField(index, "__twineId")

        val id = try {
            state.checkString(-1)
        } finally {
            state.pop(1)
        }

        return environment.registry[id]
            ?: environment.runtimeRegistry[id]
            ?: context.registry[id]
            ?: error("No native registered with ID '$id'")
    }

    @Suppress("UNCHECKED_CAST")
    private fun readEnum(
        state: LuaState,
        index: Int,
        type: Class<*>
    ): Enum<*> {
        val name = state.checkString(index)
        return java.lang.Enum.valueOf(
            type as Class<out Enum<*>>,
            name
        )
    }

    private fun isNativeTable(state: LuaState, index: Int): Boolean {
        state.getField(index, "__twineId")
        return try {
            state.isString(-1)
        } finally {
            state.pop(1)
        }
    }

    private fun readAny(
        state: LuaState,
        index: Int,
        context: TwineContext,
        environment: TwineEnvironment
    ): Any? {
        return when {
            state.isNil(index) -> null
            state.isBoolean(index) -> state.toBoolean(index)
            state.isNumber(index) -> state.checkNumber(index)
            state.isString(index) -> state.checkString(index)
            state.isTable(index) -> readTable(state, index, context, environment)
            else -> state.checkString(index)
        }
    }

    fun readFunction(
        L: LuaState,
        index: Int,
        type: Class<*>,
        returnClass: Class<*>,
        context: TwineContext,
        env: TwineEnvironment
    ): Any {
        L.pushValue(index)
        val ref = L.ref(-1)
        L.pop(1)

        val engine = context.engine
        val state = engine.state

        fun callLua(vararg args: Any?): Any? {
            state.getRef(ref)
            args.forEach { LuaValueWriter.push(state, it, engine, env) }
            state.call(args.size, 1)
            val result = if (returnClass == Unit::class.java || returnClass == Void.TYPE)
                Unit
            else
                read(state, -1, returnClass, context, env)
            state.pop(1)
            return result
        }

        // this is rotten
        val f0: () -> Any? = { callLua() }
        val f1: (Any?) -> Any? = { a -> callLua(a) }
        val f2: (Any?, Any?) -> Any? = { a, b -> callLua(a, b) }
        val f3: (Any?, Any?, Any?) -> Any? = { a, b, c -> callLua(a, b, c) }

        return when (type) {
            Function0::class.java -> f0
            Function1::class.java -> f1
            Function2::class.java -> f2
            Function3::class.java -> f3
            else -> throw TwineException("Unsupported function arity: ${type.name}")
        }
    }
}
