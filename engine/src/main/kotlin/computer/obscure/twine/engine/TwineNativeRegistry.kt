package computer.obscure.twine.engine

import computer.obscure.twine.TwineNative
import computer.obscure.twine.natives.ClassModel
import kotlin.random.Random

class TwineNativeRegistry {
    private val natives = mutableMapOf<String, Pair<TwineNative, ClassModel>>()

    private var nextId = Random.nextLong()

    fun generateId(): String =
        (nextId++).toULong().toString(36)

    fun add(native: TwineNative) {
        val key = "prerun:${generateId()}"
        native.internalId = key
        natives[key] = native to ClassModel.of(native)
    }

    fun addRuntime(native: TwineNative): String {
        findKey(native)?.let { return it }

        val key = "runtime:${generateId()}"
        native.internalId = key
        natives[key] = native to ClassModel.of(native)
        return key
    }

    fun findKey(native: TwineNative): String? =
        native.internalId?.takeIf { natives[it]?.first === native }

    fun all(): Collection<TwineNative> =
        natives.values.map { it.first }

    fun allKeys(): Collection<String> =
        natives.keys

    operator fun get(key: String): TwineNative? =
        natives[key]?.first

    fun getClassModel(key: String): ClassModel? =
        natives[key]?.second
}