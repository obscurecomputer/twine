package computer.obscure.twine.nativex.classes

import kotlin.reflect.KFunction

data class NativeProperty(
    val name: String,
    val getter: KFunction<*>,
    val setter: KFunction<*>?,
    val defaultValue: Any?
)