package computer.obscure.twine

import computer.obscure.twine.annotations.TwineNativeFunction
import computer.obscure.twine.annotations.TwineNativeProperty
import computer.obscure.twine.nativex.TwineEngine
import computer.obscure.twine.nativex.TwineNative
import org.junit.jupiter.api.Test

class TwineCodeBlockTestInstance : TwineNative("code") {
    lateinit var engine: TwineEngine

    @TwineNativeProperty
    var testProperty: String = "hello"

    @TwineNativeFunction
    fun toCodeBlock() {
        println(super.toCodeBlock(engine.globals))
    }
}

class TwineCodeBlockTest {
    fun run(script: String): Any {
        val engine = TwineEngine()

        engine.set(
            TwineCodeBlockTestInstance().apply {
                this.engine = engine
            }
        )


        return engine
            .run("test.lua", script)
            .getOrThrow()
    }

    @Test
    fun `testBaseMethod should throw`() {
        val result = run("""
            code.testProperty = "hello 10298309123"
            code.toCodeBlock()
        """.trimIndent())
    }
}