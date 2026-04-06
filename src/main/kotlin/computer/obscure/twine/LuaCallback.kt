package computer.obscure.twine

import net.hollowcube.luau.LuaState

class LuaCallback(
    private val state: LuaState,
    private val key: Int
) {
    fun invoke(vararg args: Any?) {
        val top = state.top()
        try {
            state.getGlobal("__twine_callbacks")
            state.pushInteger(key)
            state.getTable(-2)
            state.remove(-2)

            if (!state.isFunction(-1)) {
                state.pop(1)
                error("Callback $key is invalid or released")
            }

            args.forEach { arg ->
                pushValue(state, arg)
            }

            state.call(args.size, 0)
        } finally {
            state.top(top)
        }
    }

    private fun pushValue(L: LuaState, arg: Any?) {
        when (arg) {
            null -> L.pushNil()
            is String -> L.pushString(arg)
            is Number -> L.pushNumber(arg.toDouble())
            is Boolean -> L.pushBoolean(arg)
            is Map<*, *> -> {
                L.newTable()
                arg.forEach { (k, v) ->
                    L.pushString(k.toString())
                    pushValue(L, v)
                    L.setTable(-3)
                }
            }
            is TwineNative -> {
                L.pushString(arg.toString())
            }
            else -> L.pushString(arg.toString())
        }
    }

    fun release() {
        state.getGlobal("__twine_callbacks")
        state.pushNil()
        state.rawSetI(-2, key)
        state.pop(1)
    }

    companion object {
        private val nextKey = java.util.concurrent.atomic.AtomicInteger(1)

        fun capture(state: LuaState, index: Int): LuaCallback {
            state.getGlobal("__twine_callbacks")
            if (state.isNil(-1)) {
                state.pop(1)
                state.newTable()
                state.pushValue(-1)
                state.setGlobal("__twine_callbacks")
            }

            val key = nextKey.getAndIncrement()
            state.pushValue(index)
            state.rawSetI(-2, key)
            state.pop(1)

            return LuaCallback(state, key)
        }
    }
}