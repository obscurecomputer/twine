package computer.obscure.twine

import computer.obscure.twine.annotations.TwineNativeFunction
import computer.obscure.twine.annotations.TwineNativeProperty
import computer.obscure.twine.nativex.TwineEngine
import computer.obscure.twine.nativex.TwineNative
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


@Suppress("unused")
class TwineConstructorTestClass(
    @TwineNativeProperty
    val test: Int
) : TwineNative("class1") {
    @TwineNativeFunction
    fun class2(): TwineConstructorTestClass2 {
        return TwineConstructorTestClass2(this)
    }
}

@Suppress("unused")
class TwineConstructorTestClass2(
    @TwineNativeProperty
    var class1: TwineConstructorTestClass
) : TwineNative() {
    @TwineNativeProperty
    val works = true
}

class TwineIDKTest {
    fun run(script: String): Any {
        val engine = TwineEngine()

        engine.set(TwineConstructorTestClass(2))

        return engine
            .run("test.lua", script)
            .getOrThrow()
    }

    @Test
    fun `test should return a String type when a string is passed to it`() {
        val result = run("return class1.class2().works").toString()

        assertEquals("true", result)
    }
}