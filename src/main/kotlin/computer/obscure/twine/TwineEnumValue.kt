package computer.obscure.twine

import computer.obscure.twine.annotations.TwineProperty

/**
 * A specialized [TwineNative] that represents a single constant of a Kotlin Enum.
 *
 * This class stores the metadata necessary to reconstruct the original Enum constant
 * when it is passed back from Luau into Kotlin/Java code.
 *
 * @property enumClass The fully qualified name of the Enum class (e.g., "java.util.concurrent.TimeUnit").
 * @property enumName The string name of the specific enum constant (e.g., "SECONDS").
 * @property enumOrdinal The integer position of the constant in the enum declaration.
 */
class TwineEnumValue(
    val enumClass: String,
    val enumName: String,
    val enumOrdinal: Int
) : TwineNative("__enumValue_${enumClass}_${enumName}") {

    /**
     * Exposes the name of the enum constant to Luau.
     * Accessible in script via `value.name`.
     */
    @TwineProperty val name: String get() = enumName

    /**
     * Exposes the ordinal position of the enum constant to Luau.
     * Accessible in script via `value.ordinal`.
     */
    @TwineProperty val ordinal: Int get() = enumOrdinal

    /**
     * Reflectively reconstructs the actual Java [Enum] instance from the stored data.
     *
     * @return The Enum constant instance.
     * @throws ClassNotFoundException If the [enumClass] string cannot be resolved.
     * @throws ArrayIndexOutOfBoundsException If the [enumOrdinal] is invalid for the class.
     */
    fun toJava(): Enum<*> {
        @Suppress("UNCHECKED_CAST")
        val clazz = Class.forName(enumClass).enumConstants as Array<Enum<*>>
        return clazz[enumOrdinal]
    }
}