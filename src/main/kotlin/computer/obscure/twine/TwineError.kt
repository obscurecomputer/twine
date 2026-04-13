package computer.obscure.twine

/**
 * A specialized exception thrown when a Luau script fails to compile or execute.
 * * This class is used by [TwineEngine.runSafe] to provide a clean, catchable
 * error type that can be filtered through custom error handlers.
 *
 * @param message The descriptive error message.
 */
class TwineError(message: String) : Exception(message)