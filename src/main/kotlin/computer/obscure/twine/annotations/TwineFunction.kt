package computer.obscure.twine.annotations

/**
 * Annotation to mark a function as a native function in the Twine framework.
 * This allows functions to be registered as callable from Luau.
 *
 * @param name The name of the function when it is added to a table.
 *             If not specified, the default name is "INHERIT_FROM_DEFINITION",
 *             which inherits the function name.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TwineFunction(val name: String = "INHERIT_FROM_DEFINITION")