package computer.obscure.twine.exceptions

open class TwineException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)