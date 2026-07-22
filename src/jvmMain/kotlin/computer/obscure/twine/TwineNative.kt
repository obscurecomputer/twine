package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties

/**
 * Scans the class using reflection to find all functions annotated with [TwineFunction].
 *
 * @return A list of pairs mapping the Luau function name to the [KFunction] handle.
 */
fun TwineNative.getFunctions(): List<Pair<String, KFunction<*>>> {
    return this::class.functions
        .mapNotNull { func ->
            func.findAnnotation<TwineFunction>()?.let { annotation ->
                val customName = annotation.name.takeIf { it != TwineNative.INHERIT_TAG } ?: func.name
                customName to func
            }
        }
}

/**
 * Scans the class using reflection to find all properties annotated with [TwineProperty].
 *
 * @return A list of pairs mapping the Luau property name to the [KProperty] handle.
 */
fun TwineNative.getProperties(): List<Pair<String, KProperty<*>>> {
    return this::class.memberProperties
        .mapNotNull { prop ->
            prop.findAnnotation<TwineProperty>()?.let { annotation ->
                val customName = annotation.name.takeIf { it != TwineNative.INHERIT_TAG } ?: prop.name
                customName to prop
            }
        }
}