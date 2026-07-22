package computer.obscure.twine

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1

class NativeCache(klass: KClass<out TwineNative>, instance: TwineNative) {
    val functions: Map<String, List<KFunction<*>>> = instance.getFunctions()
        .groupBy({ it.first }, { it.second })

    val properties: Map<String, KProperty1<Any, *>> = instance.getProperties()
        .associate { it.first to it.second as KProperty1<Any, *> }
}