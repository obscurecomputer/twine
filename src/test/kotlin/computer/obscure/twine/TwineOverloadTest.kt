package computer.obscure.twine

import computer.obscure.twine.annotations.TwineNativeFunction
import computer.obscure.twine.annotations.TwineNativeProperty
import computer.obscure.twine.annotations.TwineOverload
import computer.obscure.twine.nativex.TwineEngine
import computer.obscure.twine.nativex.TwineNative
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals


@Suppress("unused")
class TwineOverloadTestClass : TwineNative("class1") {
    @TwineNativeFunction
    @TwineOverload
    fun test(value: String): String {
        return value
    }

    @TwineNativeFunction
    @TwineOverload
    fun test(value: Int): Int {
        return value
    }

    @TwineNativeFunction
    @TwineOverload
    fun test(value: TwineOverloadTestClass2): TwineOverloadTestClass2 {
        return value
    }

    @TwineNativeFunction
    fun class2(): TwineOverloadTestClass2 {
        return TwineOverloadTestClass2()
    }
}

@Suppress("unused")
class TwineOverloadTestClass2 : TwineNative() {
    @TwineNativeProperty
    val works = true
}

class TwineOverloadTest {
    fun run(script: String): Any {
        val engine = TwineEngine()

        engine.set(TwineOverloadTestClass())

        return engine
            .run("test.lua", script)
            .getOrThrow()
    }

    @Test
    fun `test should return a String type when a string is passed to it`() {
        val result = run("return class1.test(\"hi\")").toString()

        assertEquals("hi", result)
    }

    @Test
    fun `test should return an Int type when an int is passed to it`() {
        val result = run("return class1.test(5)").toString()

        assertEquals(5, result.toInt())
    }

    @Test
    fun `test should return a TwineNative instance when a TwineNative is passed to it`() {
        val result = run("return class1.test(class1.class2()).works").toString()

        assertEquals(true, result.toBoolean())
    }

    @Test
    fun `test should throw an error when no matching overload`() {
        val result = assertThrows<TwineError> {
            run("return class1.test(false)")
        }

        assertEquals(
            true,
            result.message?.startsWith("No matching overload found")
        )
    }
}