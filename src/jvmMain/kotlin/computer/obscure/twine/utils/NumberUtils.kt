package computer.obscure.twine.utils

import kotlin.reflect.KClass

fun Double.getTwineType(): KClass<*> =
    if (this % 1.0 == 0.0)
        Int::class
    else Double::class

fun Double.toTwineString(): String =
    if (this % 1.0 == 0.0)
        "${this.toInt()}"
    else this.toString()
