package computer.obscure.twine

import kotlinx.serialization.Serializable

/**
 * The base class for all Kotlin objects that need to be exposed to the Luau VM.
 * * Classes extending [TwineNative] can use [@TwineFunction] and [@TwineProperty]
 * annotations to mark specific members for export.
 *
 * @property valueName An optional custom name for this object in the Luau global scope.
 * If left empty, it defaults to the lowercase simple name of the class.
 */
@Serializable
open class TwineNative(
    var valueName: String = ""
) {
    /**
     * The final name used to identify this object within the Luau environment.
     *
     * Returns [valueName] if provided, otherwise uses `this::class.simpleName` in lowercase.
     */
    val resolvedName: String
        get() = valueName.ifEmpty { this::class.simpleName!!.lowercase() }

    companion object {
        /**
         * A constant value used in annotations to indicate that the
         * Luau name should be identical to the Kotlin simple name.
         */
        const val INHERIT_TAG = "INHERIT_FROM_DEFINITION"
    }
}