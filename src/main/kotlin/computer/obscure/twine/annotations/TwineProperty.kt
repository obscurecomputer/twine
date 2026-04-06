package computer.obscure.twine.annotations

/**
 * Annotation to mark a property as a native property in the Twine framework.
 * This allows the property to be exposed to Lua, making it accessible for manipulation from Lua scripts.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class TwineProperty(val name: String = "INHERIT_FROM_DEFINITION")