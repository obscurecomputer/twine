package computer.obscure.twine

import computer.obscure.twine.annotations.TwineProperty

class TwineEnumValue(
    val enumClass: String,
    val enumName: String,
    val enumOrdinal: Int
) : TwineNative("__enumValue_${enumClass}_${enumName}") {

    @TwineProperty val name: String get() = enumName
    @TwineProperty val ordinal: Int get() = enumOrdinal

    fun toJava(): Enum<*> {
        @Suppress("UNCHECKED_CAST")
        val clazz = Class.forName(enumClass).enumConstants as Array<Enum<*>>
        return clazz[enumOrdinal]
    }
}