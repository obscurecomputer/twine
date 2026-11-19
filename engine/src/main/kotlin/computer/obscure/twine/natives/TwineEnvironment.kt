package computer.obscure.twine.natives

import computer.obscure.twine.engine.TwineNativeRegistry
import java.util.UUID

class TwineEnvironment {
    val uuid = UUID.randomUUID()

    val registry = TwineNativeRegistry()
    val runtimeRegistry = TwineNativeRegistry()
}