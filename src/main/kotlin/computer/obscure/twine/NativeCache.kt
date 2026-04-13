package computer.obscure.twine

import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1

class NativeCache(val native: TwineNative) {
    val functions: Map<String, List<KFunction<*>>> = native.getFunctions()
        .groupBy({ it.first }, { it.second })

    val properties: Map<String, KProperty1<Any, *>> = native.getProperties()
        .associate { it.first to it.second as KProperty1<Any, *> }
}