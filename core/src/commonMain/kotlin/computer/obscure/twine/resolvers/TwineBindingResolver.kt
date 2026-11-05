package computer.obscure.twine.resolvers

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1

interface TwineBindingResolver {
    fun supports(type: KClass<*>): Boolean

    fun functions(instance: Any): List<Pair<String, KFunction<*>>> = emptyList()

    fun properties(instance: Any): List<Pair<String, KProperty1<Any, *>>> = emptyList()
}