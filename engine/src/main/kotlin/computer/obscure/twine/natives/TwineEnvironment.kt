package computer.obscure.twine.natives

import computer.obscure.twine.engine.TwineNativeRegistry
import java.util.UUID

class TwineEnvironment {
    val uuid = UUID.randomUUID().toString()

    val registry = TwineNativeRegistry()
    val runtimeRegistry = TwineNativeRegistry()

    var moduleLoader: ((name: String) -> String?)? = null
}