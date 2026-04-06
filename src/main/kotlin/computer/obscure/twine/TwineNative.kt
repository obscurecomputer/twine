package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties

open class TwineNative(
    var valueName: String = ""
) {
    val resolvedName: String
        get() = valueName.ifEmpty { this::class.simpleName!!.lowercase() }

    companion object {
        val INHERIT_TAG = "INHERIT_FROM_DEFINITION"
    }

    fun getFunctions(): List<Pair<String, KFunction<*>>> {
        return this::class.functions
            .mapNotNull { func ->
                func.findAnnotation<TwineFunction>()?.let { annotation ->
                    val customName = annotation.name.takeIf { it != INHERIT_TAG } ?: func.name
                    customName to func
                }
            }
    }

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