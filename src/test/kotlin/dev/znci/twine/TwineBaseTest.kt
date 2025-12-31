package dev.znci.twine

import dev.znci.twine.annotations.TwineNativeFunction
import dev.znci.twine.annotations.TwineNativeProperty
import dev.znci.twine.nativex.TwineEngine
import dev.znci.twine.nativex.TwineNative
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

        return engine.run("test.lua", script)
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