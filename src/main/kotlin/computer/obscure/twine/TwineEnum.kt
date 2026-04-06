package computer.obscure.twine

import kotlin.reflect.KClass

class TwineEnum(private val enum: KClass<*>) : TwineNative(enum.java.simpleName) {
    init {
        if (!enum.java.isEnum) error("TwineEnum can only be used with enum classes")
    }

    fun getValues(): List<TwineEnumValue> {
        @Suppress("UNCHECKED_CAST")
        return (enum.java.enumConstants as Array<Enum<*>>).map { constant ->
            TwineEnumValue(
                enumClass = enum.java.name,
                enumName = constant.name,
                enumOrdinal = constant.ordinal
            )
        }
    }

    fun isValid(value: TwineEnumValue): Boolean {
        if (value.enumClass != enum.java.name) return false
        val constants = enum.java.enumConstants ?: return false
        return value.enumOrdinal < constants.size
    }

    fun toJava(value: TwineEnumValue): Enum<*> {
        if (!isValid(value)) error("Invalid enum value: ${value.enumName}")
        @Suppress("UNCHECKED_CAST")
        return (enum.java.enumConstants as Array<Enum<*>>)[value.enumOrdinal]
    }

    companion object {
        fun <T : Enum<T>> KClass<T>.toTwineEnum() = TwineEnum(this)

        fun Enum<*>.toTwineValue() = TwineEnumValue(
            enumClass = this.declaringJavaClass.name,
            enumName = this.name,
            enumOrdinal = this.ordinal
        )
    }
}