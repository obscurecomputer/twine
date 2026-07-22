package computer.obscure.twine.utils

import kotlin.reflect.KFunction

fun KFunction<*>.getSignature(): String {
    val params = parameters.drop(1).joinToString(", ") { param ->
        val name = param.name ?: "arg"
        val type = param.type.toString().substringAfterLast(".")
        if (param.isVararg) "vararg $name: $type" else "$name: $type"
    }
    val returnType = returnType.toString().substringAfterLast(".")
    return "(${params}): $returnType"
}