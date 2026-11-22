package computer.obscure.twine.engine

import computer.obscure.twine.TwineNative
import computer.obscure.twine.natives.ClassModel
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.text.toString

class TwineNativeRegistry {
    private val natives = mutableMapOf<String, Pair<TwineNative, ClassModel>>()

    private val nextId = AtomicLong(Random.nextLong())

    fun generateId(): Long = nextId.getAndIncrement()

    fun remove(key: String) {
        natives.remove(key)
    }

    fun add(native: TwineNative) {
        val id = generateId()
        val key = "prerun:${id.toString(36)}"
        native.internalId = key
        native.internalIdLong = id
        natives[key] = native to ClassModel.of(native)
    }

    fun addRuntime(native: TwineNative): String {
        findKey(native)?.let { return it }

        val id = generateId()
        val key = "runtime:${id.toString(36)}"
        native.internalId = key
        native.internalIdLong = id
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