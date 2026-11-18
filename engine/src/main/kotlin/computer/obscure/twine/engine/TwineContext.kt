package computer.obscure.twine.engine

import computer.obscure.twine.TwineNative

class TwineContext {
    val registry = TwineNativeRegistry()
    val engine = TwineEngine(this)

    fun register(native: TwineNative) {
        registry.add(native)
    }
}