package computer.obscure.twine

class TwineValue<T : Any>(
    val value: T,
    name: String
) : TwineNative(name)