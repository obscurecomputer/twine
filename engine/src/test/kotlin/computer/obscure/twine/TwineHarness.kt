package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.engine.TwineContext
import computer.obscure.twine.natives.TwineEnvironment
import org.junit.jupiter.api.fail

class Capture(private val sink: MutableList<String>) : TwineNative("capture") {
    @TwineFunction
    fun record(value: String) {
        sink += value
    }
}

class TwineHarness {
    val output = mutableListOf<String>()
    val env = TwineEnvironment()
    val context = TwineContext()
    private val modules = mutableMapOf<String, String>()
    fun module(name: String, source: String) { modules[name] = source }

    init {
        register(Capture(output))

        context.moduleLoader = { name -> modules[name] }
    }

    fun register(native: TwineNative) {
        env.registry.add(native)
    }

    fun run(script: String) {
        context.engine.run(script.trimIndent(), env)
    }

    fun failure(script: String): Throwable {
        var thrown: Throwable? = null
        try {
            run(script)
        } catch (e: Throwable) {
            thrown = e
        }
        return thrown ?: fail("expected '$script' to fail, but it succeeded (output=$output)")
    }
}

fun Throwable.fullMessage(): String =
    generateSequence(this) { it.cause }
        .joinToString(" | ") { "${it::class.qualifiedName}: ${it.message}" }