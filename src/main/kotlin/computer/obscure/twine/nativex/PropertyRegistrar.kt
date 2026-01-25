package computer.obscure.twine.nativex

import computer.obscure.twine.annotations.TwineNativeProperty
import computer.obscure.twine.nativex.classes.NativeProperty
import computer.obscure.twine.nativex.conversion.ClassMapper.toClass
import computer.obscure.twine.nativex.conversion.Converter.toKotlinValue
import computer.obscure.twine.nativex.conversion.Converter.toLuaValue
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

class PropertyRegistrar(private val owner: TwineNative) {
    companion object {
        fun getProperties(owner: TwineNative): Map<String, NativeProperty> {
            val clazz = owner::class
            return clazz.memberProperties
                .mapNotNull { prop ->
                    val annotation = prop.findAnnotation<TwineNativeProperty>()
//                        ?: prop.getter.findAnnotation<TwineNativeProperty>()
//                        ?: clazz.primaryConstructor?.parameters?.find { it.name == prop.name }?.findAnnotation<TwineNativeProperty>()
                        ?: return@mapNotNull null

                    val name =
                        annotation.name.takeIf { it != TwineNative.INHERIT_TAG }
                            ?: prop.name

                    val getter = prop.getter
                    val setter = (prop as? KMutableProperty<*>)?.setter

                    val defaultValue = try {
                        getter.call(owner)
                    } catch (_: Exception) {
                        null
                    }

                    name to NativeProperty(
                        name = name,
                        getter = getter,
                        setter = setter,
                        defaultValue = defaultValue
                    )
                }
                .toMap()
        }
    }

    /**
     * Registers properties annotated with {@code TwineNativeProperty} into the Lua table.
     */
    fun registerProperties(props: Map<String, NativeProperty>?) {
        val properties = props ?: getProperties(owner)

        val metatable = LuaTable()

        // Handle property getting
        metatable.set("__index", object : TwoArgFunction() {
            override fun call(self: LuaValue, key: LuaValue): LuaValue {
                val prop = properties[key.tojstring()]
                    ?: return error("No property '${key.tojstring()}'")

                return try {
                    val value = prop.getter.call(owner)
                    value.toLuaValue()
                } catch (e: Exception) {
                    e.printStackTrace()
                    error("Error getting '${prop.name}': ${e.message}")
                }
            }
        })

        // Handle property setting
        metatable.set("__newindex", object : ThreeArgFunction() {
            override fun call(self: LuaValue, key: LuaValue, value: LuaValue): LuaValue {
                val prop = properties[key.tojstring()]
                    ?: return error("No property '${key.tojstring()}'")

                val setter = prop.setter
                    ?: return error("No setter found on property '${key.tojstring()}'")

                return try {
                    val setterParamType = setter.parameters[1].type
                    val convertedValue =
                        if (value.istable())
                            value.checktable().toClass(prop.setter)
                        else
                            value.toKotlinValue(setterParamType)


                    prop.setter.call(owner, convertedValue)
                    TRUE
                } catch (e: Exception) {
                    error("Error setting '${prop.name}': ${e.message}")
                }
            }
        })

        // Table-style initializer
        // test("") {
        //   property = ""
        // }
        metatable.set("__call", object : OneArgFunction() {
            override fun call(initTable: LuaValue): LuaValue {
                if (!initTable.istable()) return error("Expected a table for initialization")

                val table = initTable.checktable()
                val keys = table.keys()

                for (i in 0 until keys.size) {
                    val key = keys[i]
                    val value = initTable.get(key)

                    owner.table.set(key, value)
                }

                return owner.table
            }
        })

        val propertiesTable = LuaTable()

        for ((name, _) in properties) {
            propertiesTable.set(name, LuaValue.TRUE)
        }
        metatable.set("__properties", propertiesTable)

        owner.table.setmetatable(metatable)
    }
}