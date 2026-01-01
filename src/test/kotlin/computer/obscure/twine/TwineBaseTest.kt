package computer.obscure.twine

import computer.obscure.twine.annotations.TwineNativeFunction
import computer.obscure.twine.annotations.TwineNativeProperty
import computer.obscure.twine.nativex.TwineEngine
import computer.obscure.twine.nativex.TwineNative
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Suppress("unused")
class TwineBaseTestClass : TwineNative() {
    @TwineNativeFunction
    fun testBaseMethod(vararg args: String): String {
        return args.joinToString(", ")
    }

    @TwineNativeProperty
    val testBaseProperty: Boolean
        get() = true

    @TwineNativeProperty
    val testProperty: String
        get() = "hello"
}

class TwineBaseTest {
    fun run(script: String): Any {
        val engine = TwineEngine()

        val twineNative = TwineBaseTestClass()
        engine.setBase(twineNative)

        return engine
            .run("test.lua", script)
            .getOrThrow()
    }

    @Test
    fun `testBaseMethod should return true`() {
        val result = run("return testBaseProperty")

        assertEquals("true", result.toString())
    }

    @Test
    fun `testBaseProperty2 should return false`() {
        val result = run("return testProperty")

        assertEquals("hello", result.toString())
    }

    @Test
    fun `testBaseMethod should return the args passed into it`() {
        val result = run("return testBaseMethod('hello', 'world!')")

        assertEquals("hello, world!", result.toString())
    }
}