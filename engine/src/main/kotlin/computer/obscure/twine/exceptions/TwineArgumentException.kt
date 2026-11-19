package computer.obscure.twine.exceptions

class TwineArgumentException(
    functionName: String,
    parameter: String,
    expected: String,
    actual: String,
    cause: Throwable? = null
) : TwineException(
    "$functionName: invalid argument '$parameter': expected $expected, got $actual",
    cause
)