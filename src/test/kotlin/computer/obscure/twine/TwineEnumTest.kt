package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TwineEnumTest {
    private lateinit var engine: TwineEngine
    private val output = mutableListOf<String>()

    enum class TestEnum {
        FIRST, SECOND, THIRD
    }

    @BeforeEach
    fun setup() {
        engine = TwineEngine()
        engine.register(object : TwineNative("capture") {
            @TwineFunction
            fun record(value: String) { output.add(value) }
        })
    }

    @AfterEach
    fun teardown() {
        output.clear()
        engine.close()
    }

    @Test
    fun `reads enum property`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val value: TestEnum = TestEnum.FIRST
        })

        engine.run("capture.record(t.value)")
        assertEquals(listOf("FIRST"), output)
    }

    @Test
    fun `passes enum into function`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun accept(value: TestEnum) = value.name
        })

        engine.run(""" 
            capture.record(t.accept("SECOND"))
        """.trimIndent())

        assertEquals(listOf("SECOND"), output)
    }

    @Test
    fun `returns enum from function`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun give(): TestEnum = TestEnum.THIRD
        })

        engine.run("capture.record(t.give())")
        assertEquals(listOf("THIRD"), output)
    }

    @Test
    fun `invalid enum value throws`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun accept(value: TestEnum) = value.name
        })

        try {
            engine.run(""" 
                t.accept("NOT_REAL")
            """.trimIndent())
        } catch (_: Exception) {
            return // expected
        }

        error("Expected enum conversion to fail")
    }
}