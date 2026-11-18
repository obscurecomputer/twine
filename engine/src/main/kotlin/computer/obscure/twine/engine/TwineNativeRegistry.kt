package computer.obscure.twine.engine

import computer.obscure.twine.TwineNative
import computer.obscure.twine.natives.ClassModel

class TwineNativeRegistry {
    private val natives = mutableMapOf<String, Pair<TwineNative, ClassModel>>()

    fun add(native: TwineNative) {
        natives[native.resolvedName] = Pair(native, ClassModel.of(native))
    }

    fun all(): Collection<TwineNative> = natives.values.map { it.first }

    operator fun get(name: String): TwineNative? = natives[name]?.first
    fun getClassModel(name: String): ClassModel? = natives[name]?.second
}