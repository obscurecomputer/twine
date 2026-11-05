package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TwineEngineTest {
    private lateinit var engine: TwineEngine

    private val output = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        engine = TwineEngine()
        engine.register(object : TwineNative("capture") {
            @TwineFunction
            fun record(value: String) {
                output.add(value)
            }
        })
    }

    @AfterEach
    fun teardown() {
        output.clear()
        engine.close()
    }

    // BASIC TYPES

    @Test
    fun `returns String`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun greet() = "hello"
        })
        engine.run("capture.record(t.greet())")
        assertEquals(listOf("hello"), output)
    }

    @Test
    fun `returns Int`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun value(): Int = 42
        })
        engine.run("capture.record(tostring(t.value()))")
        assertEquals(listOf("42"), output)
    }

    @Test
    fun `returns Boolean`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun flag(): Boolean = true
        })
        engine.run("capture.record(tostring(t.flag()))")
        assertEquals(listOf("true"), output)
    }

    // PARAMETERS

    @Test
    fun `receives String parameter`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun echo(s: String) = s
        })
        engine.run("""capture.record(t.echo("hi"))""")
        assertEquals(listOf("hi"), output)
    }

    @Test
    fun `receives Int parameter`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun double(n: Double): Int = (n * 2).toInt()
        })
        engine.run("capture.record(tostring(t.double(21)))")
        assertEquals(listOf("42"), output)
    }

    // OVERLOADS

    @Test
    fun `dispatches correct overload by type`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun process(s: String) = "string:$s"

            @TwineFunction
            fun process(n: Double) = "int:$n"
        })
        engine.run("""
            capture.record(t.process("hello"))
            capture.record(t.process(42))
        """.trimIndent())
        assertEquals(listOf("string:hello", "int:42.0"), output)
    }

    @Test
    fun `dispatches correct overload by count`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun greet() = "no args"

            @TwineFunction
            fun greet(name: String) = "hello $name"
        })
        engine.run("""
            capture.record(t.greet())
            capture.record(t.greet("world"))
        """.trimIndent())
        assertEquals(listOf("no args", "hello world"), output)
    }

    @Test
    fun `throws when no overload matches`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun process(s: String) = s
        })
        assertThrows<Exception> {
            engine.run("t.process(123)")
        }
    }

    // PASS TEST

    @Test
    fun `passes TwineNative as parameter`() {
        var received: TwineNative? = null
        val inner = object : TwineNative("inner") {}
        engine.register(inner)
        engine.register(object : TwineNative("outer") {
            @TwineFunction
            fun use(n: TwineNative) { received = n }
        })
        engine.run("outer.use(inner)")
        assertEquals(inner, received)
    }

    @Test
    fun `returns TwineNative and can call methods on it`() {
        engine.register(object : TwineNative("factory") {
            @TwineFunction
            fun create(): TwineNative = object : TwineNative("product") {
                @TwineFunction
                fun name() = "created"
            }
        })
        engine.run("""
            local p = factory.create()
            capture.record(p.name())
        """.trimIndent())
        assertEquals(listOf("created"), output)
    }

    // NULL/UNIT RETURN

    @Test
    fun `Unit return pushes nothing`() {
        var called = false
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun doSomething() { called = true }
        })
        engine.run("t.doSomething()")
        assertEquals(true, called)
    }
}