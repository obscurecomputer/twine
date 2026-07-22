package computer.obscure.twine.annotations

/**
 * Annotation to mark a property as a native property in the Twine framework.
 * This allows the property to be exposed to Luau, making it accessible for manipulation from Luau scripts.
 *
 * @param name The name of the property when it is added to a table.
 *             If not specified, the default name is "INHERIT_FROM_DEFINITION",
 *             which inherits the property name.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class TwineProperty(val name: String = "INHERIT_FROM_DEFINITION")