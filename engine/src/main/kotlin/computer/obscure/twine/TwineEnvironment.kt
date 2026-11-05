package computer.obscure.twine

import net.hollowcube.luau.LuaState

class TwineEnvironment {
    private val entries = mutableMapOf<String, Any?>()

    fun register(name: String, value: Any?) {
        entries[name] = value
    }

    fun register(native: TwineNative) {
        entries[native.resolvedName] = native
    }

    internal fun applyTo(L: LuaState, engine: TwineEngine) {
        L.newTable()

        entries.forEach { (name, value) ->
            TwineLogger.debug("Inserting $name: $value")
            if (value is TwineNative) {
                engine.pushNativeTable(L, value)
            } else {
                LuaTypeResolver.push(L, value, value?.let { it::class }) { innerL, native ->
                    engine.pushNativeTable(innerL, native)
                }
            }
            L.setField(-2, name)
        }

        L.newTable()
        L.getGlobal("_G")
        L.setField(-2, "__index")
        L.setMetaTable(-2)

        L.replace(LuaRegistry.GLOBALS)
    }
}