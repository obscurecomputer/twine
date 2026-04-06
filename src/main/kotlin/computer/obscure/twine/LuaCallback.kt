package computer.obscure.twine

import net.hollowcube.luau.LuaState

/**
 * A wrapper for Luau functions that allows Kotlin to safely call
 * Lua callbacks across JNI.
 *
 * This class handles the persistence of the function within Luau's VM's
 * global state to prevent GC while the callback is active.
 *
 * @property state The [LuaState] the function is in.
 * @property key The unique integer identifier user to retrieve the function from the registry
 */
class LuaCallback(
    private val state: LuaState,
    private val key: Int
) {
    /**
     * Executes the wrapped Luau function with the provided arguments.
     *
     * @param args The parameters to pass to the Lua function. Supports null, Strings, Numbers,
     * Booleans, Maps, and [TwineNative]s.
     * @throws IllegalStateException If the function has been released or is no longer valid.
     */
    fun invoke(vararg args: Any?) {
        state.getGlobal("__twine_callbacks")
        state.pushInteger(key)
        state.getTable(-2)

        // Remove the registry table from stack, leaving just the function
        state.remove(-2)

        if (!state.isFunction(-1)) {
            state.pop(1)
            error("Callback $key is invalid or released")
        }

        // Push args onto stack
        args.forEach { arg ->
            LuaTypeResolver.push(state, arg, arg?.let { it::class }) { L, native ->
                L.pushString(native.toString())
            }
        }

        state.call(args.size, 0)
    }

    /**
     * Removes this function from the Luau registry.
     * * Once called, this [LuaCallback] instance can no longer be invoked and
     * the underlying function becomes GCable.
     */
    fun release() {
        state.getGlobal("__twine_callbacks")
        state.pushNil()
        state.rawSetI(-2, key)
        state.pop(1)
    }

    companion object {
        private val nextKey = java.util.concurrent.atomic.AtomicInteger(1)

        /**
         * Captures a function from the current Luau stack and stores it in a
         * protected registry to prevent it being GCed.
         *
         * @param state The current [LuaState].
         * @param index The stack index where the Lua function is located.
         * @return A [LuaCallback] instance that can trigger the function later.
         */
        fun capture(state: LuaState, index: Int): LuaCallback {
            state.getGlobal("__twine_callbacks")
            if (state.isNil(-1)) {
                state.pop(1)
                state.newTable()
                state.pushValue(-1)
                state.setGlobal("__twine_callbacks")
            }

            // Give the registry a unique ID
            val key = nextKey.getAndIncrement()
            state.pushValue(index)
            state.rawSetI(-2, key) // registry[key] = function
            state.pop(1)

            return LuaCallback(state, key)
        }
    }
}