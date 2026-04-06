package computer.obscure.twine

import net.hollowcube.luau.LuaState
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

object LuaTypeResolver {
    fun matches(L: LuaState, index: Int, type: KClass<*>?, natives: Map<String, TwineNative>): Boolean {
        return when {
            type == String::class -> L.isString(index)
            type == Double::class || type == Float::class -> L.isNumber(index)
            type == Boolean::class -> L.isBoolean(index)
            type != null && type.java.isEnum -> L.isString(index)
            type != null && type.isSubclassOf(TwineNative::class) -> L.isTable(index)
            type == Any::class -> true
            type == LuaCallback::class -> L.isFunction(index)
            else -> false
        }
    }

    fun read(L: LuaState, index: Int, type: KClass<*>?, natives: Map<String, TwineNative>): Any? {
        return when {
            type == String::class -> L.checkString(index)
            type == Int::class -> L.checkInteger(index).toInt()
            type == Long::class -> L.checkInteger(index)
            type == Double::class -> L.checkNumber(index)
            type == Float::class -> L.checkNumber(index).toFloat()
            type == Boolean::class -> L.toBoolean(index)
            type == LuaCallback::class -> readCallback(L, index)
            type != null && type.isSubclassOf(TwineNative::class) -> readNative(L, index, natives)
            type != null && type.java.isEnum -> {
                val name = L.checkString(index)
                java.lang.Enum.valueOf(type.java as Class<out Enum<*>>, name)
            }
            else -> when {
                L.isNumber(index) -> L.checkNumber(index)
                L.isBoolean(index) -> L.toBoolean(index)
                L.isTable(index) -> readNative(L, index, natives)
                else -> L.checkString(index)
            }
        }
    }

    fun push(L: LuaState, value: Any?, type: KClass<*>?, pushTable: (LuaState, TwineNative) -> Unit): Int {
        if (value == null) {
            L.pushNil()
            return 1
        }

        return when (value) {
            is Boolean -> {
                L.pushBoolean(value)
                1
            }
            is Number -> {
                if (value is Double || value is Float) {
                    L.pushNumber(value.toDouble())
                } else {
                    L.pushInteger(value.toInt())
                }
                1
            }
            is TwineNative -> {
                pushTable(L, value)
                1
            }
            is String -> {
                L.pushString(value)
                1
            }
            else -> {
                if (value.javaClass.isEnum) {
                    L.pushString(value.toString())
                } else {
                    L.pushString(value.toString())
                }
                1
            }
        }
    }

    private fun readNative(L: LuaState, index: Int, natives: Map<String, TwineNative>): TwineNative {
        L.getField(index, "__twineName")
        val name = L.checkString(-1)
        L.pop(1)
        return natives[name] ?: error("No native registered with name '$name'")
    }

    private fun readCallback(L: LuaState, index: Int): LuaCallback {
        return LuaCallback.capture(L, index)
    }
}