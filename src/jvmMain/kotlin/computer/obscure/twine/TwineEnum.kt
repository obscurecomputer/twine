package computer.obscure.twine

import kotlin.reflect.KClass

/**
 * A wrapper class that exposes Kotlin Enum classes to the Luau environment.
 * * In Luau, this is a table where each key is an enum constant name
 * and each value is a [TwineEnumValue] proxy.
 *
 * @param enum The [KClass] of the enum to be wrapped.
 * @throws IllegalStateException if the provided class is not actually an enum.
 */
class TwineEnum(private val enum: KClass<*>) : TwineNative(enum.java.simpleName) {
    init {
        if (!enum.java.isEnum) error("TwineEnum can only be used with enum classes")
    }

    /**
     * Retrieves all constants defined in the wrapped enum.
     *
     * @return A list of [TwineEnumValue] objects representing the enum's constants.
     */
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

    /**
     * Checks if a given [TwineEnumValue] is a valid member of this specific enum.
     * Verifies both the class name and the ordinal bounds.
     *
     * @param value The value to validate.
     * @return `true` if the value belongs to this enum type.
     */
    fun isValid(value: TwineEnumValue): Boolean {
        if (value.enumClass != enum.java.name) return false
        val constants = enum.java.enumConstants ?: return false
        return value.enumOrdinal < constants.size
    }

    /**
     * Converts a Twine-wrapped enum value back into a real Java [Enum] constant.
     *
     * @param value The [TwineEnumValue] received from the Luau VM.
     * @return The actual Java Enum constant.
     * @throws IllegalStateException if the value is not valid for this enum.
     */
    fun toJava(value: TwineEnumValue): Enum<*> {
        if (!isValid(value)) error("Invalid enum value: ${value.enumName}")
        @Suppress("UNCHECKED_CAST")
        return (enum.java.enumConstants as Array<Enum<*>>)[value.enumOrdinal]
    }

    companion object {
        /**
         * Extension function to easily convert a Kotlin Enum class to a [TwineEnum].
         * Usage: `TestEnum::class.toTwineEnum()`
         * * @return A new [TwineEnum] instance.
         */
        fun <T : Enum<T>> KClass<T>.toTwineEnum() = TwineEnum(this)

        /**
         * Extension function to convert a specific Enum instance into a [TwineEnumValue].
         * This is used when returning an enum value from Kotlin to Luau.
         * @return A [TwineEnumValue] data transfer object.
         */
        fun Enum<*>.toTwineValue() = TwineEnumValue(
            enumClass = this.declaringJavaClass.name,
            enumName = this.name,
            enumOrdinal = this.ordinal
        )
    }
}