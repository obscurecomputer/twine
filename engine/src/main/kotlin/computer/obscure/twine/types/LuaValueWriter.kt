
package computer.obscure.twine.types

import computer.obscure.twine.TwineNative
import computer.obscure.twine.engine.TwineEngine
import computer.obscure.twine.natives.TwineEnvironment
import net.hollowcube.luau.LuaState
import java.util.Optional

object LuaValueWriter {
    fun push(
        state: LuaState,
        value: Any?,
        engine: TwineEngine,
        env: TwineEnvironment
    ) {
        when (value) {
            null -> state.pushNil()
            is Boolean -> state.pushBoolean(value)
            is Double, is Float -> state.pushNumber((value as Number).toDouble())
            is Number -> state.pushInteger(value.toInt())
            is String -> state.pushString(value)
            is Enum<*> -> state.pushString(value.name)

            is TwineNative ->
                engine.pushNative(state, value, env)

            is Map<*, *> -> {
                state.newTable()
                value.forEach { (key, item) ->
                    state.pushString(key.toString())
                    push(state, item, engine, env)
                    state.setTable(-3)
                }
            }

            is Iterable<*> -> {
                state.newTable()
                value.forEachIndexed { index, item ->
                    push(state, item, engine, env)
                    state.rawSetI(-2, index + 1)
                }
            }

            is Array<*> -> {
                state.newTable()
                value.forEachIndexed { index, item ->
                    push(state, item, engine, env)
                    state.rawSetI(-2, index + 1)
                }
            }

            is Optional<*> ->
                push(state, value.orElse(null), engine, env)

            else -> state.pushString(value.toString())
        }
    }
}
