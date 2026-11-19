package computer.obscure.twine.exceptions

class TwineExecutionException(
    functionName: String,
    cause: Throwable
) : TwineException(
    "$functionName threw an exception: ${cause.message ?: cause.javaClass.simpleName}",
    cause
)