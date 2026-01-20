package computer.obscure.twine

import computer.obscure.twine.nativex.TwineEngine
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TwineErrorTest {
    fun run(script: String): Any {
        val engine = TwineEngine()

        val twineNative = TwineBaseTestClass()
        engine.setBase(twineNative)

        return engine
            .run("test.lua", script)
            .getOrThrow()
    }

    @Test
    fun `testBaseMethod should throw`() {
        val exception = assertThrows<TwineError> {
            run("return test.test")
        }

        assertEquals(
            "Lua error in test.lua:1 – attempted to index a nil value",
            exception.message
        )
    }
}