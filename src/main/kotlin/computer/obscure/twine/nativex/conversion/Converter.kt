package computer.obscure.twine.nativex.conversion

import computer.obscure.twine.TableSetOptions
import computer.obscure.twine.TwineEnum
import computer.obscure.twine.TwineEnumValue
import computer.obscure.twine.TwineError
import computer.obscure.twine.TwineLogger
import computer.obscure.twine.TwineLuaValue
import computer.obscure.twine.TwineTable
import computer.obscure.twine.nativex.TwineNative
import computer.obscure.twine.nativex.conversion.ClassMapper.toClass
import org.luaj.vm2.LuaBoolean
import org.luaj.vm2.LuaInteger
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import kotlin.collections.get
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

object Converter {
    fun KFunction<*>.getSignature(): String {
        val params = parameters.drop(1).joinToString(", ") { param ->
            val name = param.name ?: "arg"
            val type = param.type.toString().substringAfterLast(".")
            if (param.isVararg) "vararg $name: $type" else "$name: $type"
        }
        val returnType = returnType.toString().substringAfterLast(".")
        return "$name($params): $returnType"
    }

    /**
     * Converts Lua arguments to Kotlin arguments based on function parameter types.
     *
     * @param func The function whose parameters should be converted.
     * @return An array of Kotlin compatible arguments.
     */
    fun Varargs.toKotlinArgs(func: KFunction<*>, isColonCall: Boolean = false): Array<Any?> {
        val params = func.parameters.drop(1) // Skip `this`
        val luaArgOffset = if (isColonCall) 1 else 0

        val lastParam = params.lastOrNull()
        val isVararg = lastParam?.isVararg == true
        val fixedParamCount = if (isVararg) params.size - 1 else params.size

        if (narg() < fixedParamCount) {
            throw TwineError("Not enough arguments for ${func.name}. Expected ${fixedParamCount}, got ${narg()}")
        }

        if (!isVararg && narg() != fixedParamCount) {
            throw TwineError("Invalid number of arguments for ${func.name}. Expected ${fixedParamCount}, got ${narg()}")
        }

        if (lastParam?.isVararg == true) {
            val count = narg() - luaArgOffset
            val varargKType = lastParam.type
            val varargClassifier = varargKType.classifier as? KClass<*>

            val varargArray: Any = when (varargClassifier) {
                DoubleArray::class -> DoubleArray(count) { this.arg(it + 1 + luaArgOffset).todouble() }
                IntArray::class -> IntArray(count) { this.arg(it + 1 + luaArgOffset).toint() }
                BooleanArray::class -> BooleanArray(count) { this.arg(it + 1 + luaArgOffset).toboolean() }
                LongArray::class -> LongArray(count) { this.arg(it + 1 + luaArgOffset).tolong() }
                FloatArray::class -> FloatArray(count) { this.arg(it + 1 + luaArgOffset).tofloat() }

                // object arrays (strings, TwineNatives, etc.) fall under here
                // ones that do not have kotlin equivalents
                else -> {
                    val componentKType = varargKType.arguments.firstOrNull()?.type
                    val componentClass = (componentKType?.classifier as? KClass<*>)?.java
                        ?: Any::class.java

                    val typedArray = java.lang.reflect.Array.newInstance(componentClass, count)

                    for (i in 0 until count) {
                        val value = this.arg(i + 1 + luaArgOffset).toKotlinValue(componentKType)
                        java.lang.reflect.Array.set(typedArray, i, value)
                    }
                    typedArray
                }
            }

            return arrayOf(varargArray)
        }

        if (isVararg) {
            val varargParam = params.last()
            val varargArgs = ArrayList<Any?>()
            for (i in fixedParamCount until narg()) {
                val arg = this.arg(i + 1)
                varargArgs.add(arg.toKotlinValue(varargParam.type))
            }
            return arrayOf(varargArgs.toTypedArray())
        }

        return params.mapIndexed { index, param ->
            this.arg(index + 1 + luaArgOffset).let { arg ->
                TwineLogger.debug("Converting arg $index for param ${param.name} (type: ${param.type})")
                TwineLogger.debug("Lua value type: ${arg.typename()}, istable: ${arg.istable()}")

                when {
                    arg is TwineTable -> arg
                    arg.istable() -> {
                        val table = arg.checktable()
                        val metatable = table.getmetatable()

                        if (metatable != null && !metatable.get("__twine_native").isnil()) {
                            TwineLogger.debug("Found __twine_native, getting userdata")
                            metatable.get("__twine_native").checkuserdata()
                        } else {
                            TwineLogger.debug("Calling toClass for table")
                            table.toClass(func)
                        }
                    }
                    else -> {
                        TwineLogger.debug("Calling toKotlinValue")
                        arg.toKotlinValue(param.type)
                    }
                }
            }
        }.toTypedArray()
    }

    /**
     * Converts a LuaValue to a Kotlin compatible value based on its type.
     *
     * @param type The expected Kotlin type.
     * @return The converted value in Kotlin.
     */
    fun LuaValue.toKotlinValue(type: KType?): Any? {
        if (isnil()) return null
        val classifier = type?.classifier as? KClass<*>

        if (classifier != null && classifier.isInstance(this)) {
            return this
        }

        if (isuserdata()) {
            val obj = this.touserdata()
            if (classifier != null && classifier.isInstance(obj)) {
                return obj
            }
        }

        val converters: Map<KClass<*>, () -> Any?> = mapOf(
            String::class to { if (isnil()) null else tojstring() },
            Boolean::class to { toboolean() },
            Int::class to { toint() },
            Double::class to { todouble() },
            Float::class to { tofloat() },
            Long::class to { tolong() }
        )

        if (isfunction() && classifier?.java?.name?.startsWith("kotlin.jvm.functions.Function") == true) {
            val luaFunc = this.checkfunction()
            return when (classifier) {
                Function0::class -> {
                    val fn: Function0<Any?> = {
                        val result = luaFunc.call()
                        result.toKotlinValue(null)
                    }
                    fn
                }

                Function1::class -> {
                    val fn: Function1<Any?, Any?> = { arg1 ->
                        val result = luaFunc.call(arg1.toLuaValue())
                        result.toKotlinValue(null)
                    }
                    fn
                }

                Function2::class -> {
                    val fn: Function2<Any?, Any?, Any?> = { arg1, arg2 ->
                        val result = luaFunc.call(arg1.toLuaValue(), arg2.toLuaValue())
                        result.toKotlinValue(null)
                    }
                    fn
                }

                Function3::class -> {
                    val fn: Function3<Any?, Any?, Any?, Any?> = { arg1, arg2, arg3 ->
                        val result = luaFunc.call(arg1.toLuaValue(), arg2.toLuaValue(), arg3.toLuaValue())
                        result.toKotlinValue(null)
                    }
                    fn
                }

                else -> {
                    throw TwineError("Unsupported function type: $classifier")
                }
            }
        }

        if (classifier == TwineEnumValue::class && istable()) {
            val table = checktable()
            val ordinal = table.get("enumOrdinal").toint()
            val name = table.get("enumName").tojstring()
            val parent = table.get("parentName").tojstring()
            val clazz = table.get("__javaClass").tojstring()

            return TwineEnumValue(parent, name, ordinal, clazz)
        }

        converters[classifier]?.let { return it() }

        return this
    }

    /**
     * Converts a Kotlin value to a LuaValue.
     * This function is mostly used for figuring out the return type of a function.
     * Such as a TwineNativeFunction that returns a TwineTable,
     * which would be converted to a lua-compatible LuaTable.
     *
     * @return The corresponding LuaValue.
     */
    fun Any?.toLuaValue(): LuaValue {
        return when (this) {
            is String -> LuaValue.valueOf(this)
            is Boolean -> LuaValue.valueOf(this)
            is Double -> LuaValue.valueOf(this)
            is Int -> LuaValue.valueOf(this)
            is Long -> LuaValue.valueOf(this.toInt())
            is Float -> LuaValue.valueOf(this.toDouble())
            is LuaString -> LuaValue.valueOf(this.toString())
            is LuaInteger -> LuaValue.valueOf(this.toint())
            is LuaBoolean -> LuaValue.valueOf(this.toboolean())
            is LuaTable -> this
            is TwineNative -> {
                this.set("__javaClass", TableSetOptions(getter = { LuaValue.valueOf(this.javaClass.name) }))

                val metatable = table.getmetatable() ?: LuaTable()
                metatable.set("__twine_native", LuaValue.userdataOf(this))
                table.setmetatable(metatable)
                table
            }
            is TwineTable -> {
                val table = this.table
                set("__javaClass", TableSetOptions(getter = { LuaValue.valueOf(javaClass.name) }))
                table
            }
            is TwineLuaValue -> {
                throw TwineError("TwineLuaValue should not be used as a return type.")
            }
            is Array<*> -> {
                val table = LuaTable()
                this.forEachIndexed { index, value ->
                    table.set(index + 1, value.toLuaValue())
                }
                table
            }
            is Set<*> -> {
                val table = LuaTable()
                this.forEachIndexed { index, value ->
                    table.set(index + 1, value.toLuaValue())
                }
                table
            }
            is java.util.ArrayList<*> -> {
                val table = LuaTable()
                this.forEachIndexed { index, value ->
                    table.set(index + 1, value.toLuaValue())
                }
                table
            }
            is Enum<*> -> {
                val enumClass = this::class
                val enumTable = TwineEnum(enumClass)
                enumTable.toLuaTable()
            }
            null, Unit -> TwineLuaValue.NIL
            else -> {
                throw TwineError("Unsupported toLuaValue type: ${this.javaClass.simpleName ?: "null"}")
            }
        }
    }

    /**
     * Converts a Lua value to the corresponding Kotlin type.
     */
    fun LuaValue.toKotlinType(): KType {
        return when {
            isboolean() -> Boolean::class.createType()
            isint() -> Int::class.createType()
            isnumber() -> Double::class.createType()
            isstring() -> String::class.createType()
            isuserdata() -> {
                val obj = this.touserdata()
                obj::class.createType()
            }
            isfunction() -> {
                Function::class.createType(listOf(KTypeProjection.STAR))
            }
            istable() -> {
                val table = checktable()
                val className = table.get("__javaClass").optjstring(null)
                if (className != null) {
                    try {
                        val clazz = Class.forName(className).kotlin
                        clazz.createType()
                    } catch (_: Exception) {
                        table::class.createType()
                    }
                } else {
                    table::class.createType()
                }
            }

            else -> this::class.createType()
        }
    }
}