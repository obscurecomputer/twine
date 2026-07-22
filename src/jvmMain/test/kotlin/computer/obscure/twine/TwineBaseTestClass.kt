package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Suppress("unused")
class TwineBaseTestClass : TwineNative() {
    @TwineFunction
    fun greet(name: String): String = "Hello, $name!"

    @TwineProperty
    val isActive: Boolean
        get() = true

    @TwineProperty
    val defaultName: String
        get() = "TwineUser"
}

class TwineBaseEngineTest {

    private fun runScript(script: String): Any {
        val engine = TwineEngine()
        val base = TwineBaseTestClass()
        engine.setBase(base)
        return engine.run(script)!!
    }

    @Test
    fun `test function in global context`() {
        val result = runScript("return greet('bye')")
        assertEquals("Hello, bye!", result.toString())
    }

    @Test
    fun `test boolean property in global context`() {
        val result = runScript("return isActive")
        assertEquals("true", result.toString())
    }

    @Test
    fun `test string property in global context`() {
        val result = runScript("return defaultName")
        assertEquals("TwineUser", result.toString())
    }

    @Test
    fun `test multiple calls`() {
        val r1 = runScript("return greet('chxll')")
        val r2 = runScript("return isActive")
        val r3 = runScript("return defaultName")

        assertEquals("Hello, chxll!", r1.toString())
        assertEquals("true", r2.toString())
        assertEquals("TwineUser", r3.toString())
    }
}