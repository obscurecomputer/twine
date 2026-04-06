package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties

/**
 * The base class for all Kotlin objects that need to be exposed to the Luau VM.
 * * Classes extending [TwineNative] can use [@TwineFunction] and [@TwineProperty]
 * annotations to mark specific members for export.
 *
 * @property valueName An optional custom name for this object in the Lua global scope.
 * If left empty, it defaults to the lowercase simple name of the class.
 */
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
         * Lua name should be identical to the Kotlin simple name.
         */
        const val INHERIT_TAG = "INHERIT_FROM_DEFINITION"
    }

    /**
     * Scans the class using reflection to find all functions annotated with [TwineFunction].
     *
     * @return A list of pairs mapping the Lua function name to the [KFunction] handle.
     */
    fun getFunctions(): List<Pair<String, KFunction<*>>> {
        return this::class.functions
            .mapNotNull { func ->
                func.findAnnotation<TwineFunction>()?.let { annotation ->
                    val customName = annotation.name.takeIf { it != INHERIT_TAG } ?: func.name
                    customName to func
                }
            }
    }

    /**
     * Scans the class using reflection to find all properties annotated with [TwineProperty].
     *
     * @return A list of pairs mapping the Lua property name to the [KProperty] handle.
     */
    fun getProperties(): List<Pair<String, KProperty<*>>> {
        return this::class.memberProperties
            .mapNotNull { prop ->
                prop.findAnnotation<TwineProperty>()?.let { annotation ->
                    val customName = annotation.name.takeIf { it != INHERIT_TAG } ?: prop.name
                    customName to prop
                }
            }
    }
}