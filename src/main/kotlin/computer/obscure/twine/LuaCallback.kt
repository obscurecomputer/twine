package computer.obscure.twine

import net.hollowcube.luau.LuaState
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

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
    private val key: Int,
    private val engine: WeakReference<TwineEngine>
) {
    /**
     * Executes the wrapped Luau function with the provided arguments.
     *
     * @param args The parameters to pass to the Lua function. Supports null, Strings, Numbers,
     * Booleans, Maps, and [TwineNative]s.
     * @throws IllegalStateException If the function has been released or is no longer valid.
     */
    fun invoke(vararg args: Any?) {
        val eng = engine.get() ?: throw IllegalStateException("Engine GCed")
        if (eng.closed) throw IllegalStateException("Engine is closed")

        synchronized(eng.stateLock) {
            val L = eng.state
            val initialTop = L.top()
            val pin = eng.framePin.get()
            val pinSizeBefore = pin.size

            try {
                L.getGlobal("__twine_callbacks")
                if (L.isNil(-1)) throw IllegalStateException("Callback registry not initialized")

                L.pushInteger(key)
                L.getTable(-2)
                L.remove(-2)

                if (L.isNil(-1)) throw IllegalStateException("Callback has been released")
                if (!L.isFunction(-1)) throw IllegalStateException("Registry entry is not a function")

                args.forEach { arg ->
                    LuaTypeResolver.push(L, arg, arg?.let { it::class }) { innerL, native ->
                        eng.pushNativeTable(innerL, native)
                    }
                }

                L.call(args.size, 0)

            } catch (e: Exception) {
                if (e is IllegalStateException) throw e
                TwineLogger.error("Callback $key failed: ${e.message}")
            } finally {
                while (pin.size > pinSizeBefore) pin.removeAt(pin.size - 1)

                // !! Reset the stack pointer
                // !! VERY IMPORTANT !! DO NOT REMOVE !!
                L.top(initialTop)
            }
        }
    }

    fun <T> call(vararg args: Any?): T {
        val value = call(*args)
        if (value.isEmpty()) return null as T
        return value.last() as T
    }

    fun call(vararg args: Any?): List<Any?> {
        val eng = engine.get() ?: throw IllegalStateException("Engine GCed")
        if (eng.closed) throw IllegalStateException("Engine is closed")

        synchronized(eng.stateLock) {
            val L = eng.state
            val initialTop = L.top()
            val pin = eng.framePin.get()
            val pinSizeBefore = pin.size

            try {
                L.getGlobal("__twine_callbacks")
                if (L.isNil(-1)) throw IllegalStateException("Callback registry not initialized")

                L.pushInteger(key)
                L.getTable(-2)
                L.remove(-2)

                if (L.isNil(-1)) throw IllegalStateException("Callback has been released")
                if (!L.isFunction(-1)) throw IllegalStateException("Registry entry is not a function")

                args.forEach { arg ->
                    LuaTypeResolver.push(L, arg, arg?.let { it::class }) { innerL, native ->
                        eng.pushNativeTable(innerL, native)
                    }
                }

                L.call(args.size, -1)

                val returnCount = L.top() - initialTop
                return (1..returnCount).map { i ->
                    LuaTypeResolver.read(L, initialTop + i, null, eng.allNatives)
                }

            } catch (e: Exception) {
                if (e is IllegalStateException) throw e
                TwineLogger.error("Callback $key failed: ${e.message}")
                return emptyList()
            } finally {
                while (pin.size > pinSizeBefore) pin.removeAt(pin.size - 1)
                L.top(initialTop)
            }
        }
    }

    /**
     * Removes this function from the Luau registry.
     * * Once called, this [LuaCallback] instance can no longer be invoked and
     * the underlying function becomes GCable.
     */
    fun release() {
        if (engine.get()?.closed == true) return

        state.getGlobal("__twine_callbacks")
        state.pushNil()
        state.rawSetI(-2, key)
        state.pop(1)
    }

    companion object {
        /**
         * Increments every time a new function is captured to ensure no callbacks collide
         */
        private val nextKey = AtomicInteger(1)

        /**
         * Captures a function from the current Luau stack and stores it in a
         * protected registry to prevent it being GCed.
         *
         * @param state The current [LuaState].
         * @param index The stack index where the Lua function is located.
         * @param engine A [WeakReference] containing the [TwineEngine].
         * @return A [LuaCallback] instance that can trigger the function later.
         */
        fun capture(state: LuaState, index: Int, engine: WeakReference<TwineEngine>): LuaCallback {
            val eng = engine.get() ?: error("Engine GCed during capture")

            // Lock the state so >2 threads don't push/pop at the same time
            // and corrupt the stack
            synchronized(eng.stateLock) {
                // !! Save the current height of the stack so we can
                // !! return to it later. Do NOT remove !!
                val initialTop = state.top()
                try {
                    // Initialize the global registry table if it doesn't exist yet
                    state.getGlobal("__twine_callbacks")
                    if (state.isNil(-1)) {
                        state.pop(1)
                        state.newTable()
                        state.pushValue(-1)
                        state.setGlobal("__twine_callbacks")
                    }

                    // Assign a unique ID and store the function
                    val key = nextKey.getAndIncrement()

                    // Copy the function at [index] to the top of the stack
                    state.pushValue(index)
                    // Store the top value in the table at -2 with key [key]
                    state.rawSetI(-2, key)

                    return LuaCallback(state, key, engine)
                } finally {
                    // !! Reset the stack pointer
                    // !! VERY IMPORTANT !! DO NOT REMOVE !!
                    state.top(initialTop)
                }
            }
        }
    }
}