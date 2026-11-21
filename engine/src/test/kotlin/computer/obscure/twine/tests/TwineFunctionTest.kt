package computer.obscure.twine.tests

import computer.obscure.twine.TwineHarness
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.fullMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwineFunctionTest {
    private lateinit var h: TwineHarness

    @BeforeEach
    fun setup() {
        h = TwineHarness()
    }

    // -- return types --

    @Test
    fun `returns String`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun greet() = "hello"
        })
        h.run("capture.record(t.greet())")
        assertEquals(listOf("hello"), h.output)
    }

    @Test
    fun `returns Int`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun value(): Int = 42
        })
        h.run("capture.record(tostring(t.value()))")
        assertEquals(listOf("42"), h.output)
    }

    @Test
    fun `returns Double`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun value(): Double = 3.5
        })
        h.run("capture.record(tostring(t.value()))")
        assertEquals(listOf("3.5"), h.output)
    }

    @Test
    fun `returns Boolean`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun flag(): Boolean = true
        })
        h.run("capture.record(tostring(t.flag()))")
        assertEquals(listOf("true"), h.output)
    }

    @Test
    fun `returned value has the matching Luau type`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun s() = "x"
            @TwineFunction fun n() = 1.0
            @TwineFunction fun b() = false
        })
        h.run("""
            capture.record(type(t.s()))
            capture.record(type(t.n()))
            capture.record(type(t.b()))
        """)
        assertEquals(listOf("string", "number", "boolean"), h.output)
    }

    @Test
    fun `Unit return runs the function and yields nothing`() {
        var called = false
        h.register(object : TwineNative("t") {
            @TwineFunction fun doSomething() { called = true }
        })
        h.run("""
            local result = t.doSomething()
            capture.record(tostring(result))
        """)
        assertTrue(called)
        assertEquals(listOf("nil"), h.output)
    }

    @Test
    fun `null return becomes nil`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun maybe(): String? = null
        })
        h.run("capture.record(tostring(t.maybe()))")
        assertEquals(listOf("nil"), h.output)
    }

    // -- parameters --

    @Test
    fun `receives String parameter`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun echo(s: String) = s
        })
        h.run("""capture.record(t.echo("hi"))""")
        assertEquals(listOf("hi"), h.output)
    }

    @Test
    fun `receives Double parameter`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun double(n: Double): Double = n * 2
        })
        h.run("capture.record(tostring(t.double(21)))")
        assertEquals(listOf("42"), h.output)
    }

    @Test
    fun `receives Int parameter`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun inc(n: Int): Int = n + 1
        })
        h.run("capture.record(tostring(t.inc(41)))")
        assertEquals(listOf("42"), h.output)
    }

    @Test
    fun `receives Boolean parameter`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun invert(b: Boolean) = !b
        })
        h.run("capture.record(tostring(t.invert(true)))")
        assertEquals(listOf("false"), h.output)
    }

    @Test
    fun `receives several parameters of different types`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun describe(name: String, age: Int, active: Boolean) = "$name/$age/$active"
        })
        h.run("""capture.record(t.describe("sam", 30, true))""")
        assertEquals(listOf("sam/30/true"), h.output)
    }

    @Test
    fun `arguments are passed in order`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun sub(a: Double, b: Double) = a - b
        })
        h.run("capture.record(tostring(t.sub(10, 4)))")
        assertEquals(listOf("6"), h.output)
    }

    // -- scripting --

    @Test
    fun `can be called repeatedly from a loop`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun twice(n: Double) = n * 2
        })
        h.run("""
            for i = 1, 3 do
                capture.record(tostring(t.twice(i)))
            end
        """)
        assertEquals(listOf("2", "4", "6"), h.output)
    }

    @Test
    fun `return value can feed another native call`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun name() = "world"
            @TwineFunction fun greet(who: String) = "hello $who"
        })
        h.run("capture.record(t.greet(t.name()))")
        assertEquals(listOf("hello world"), h.output)
    }

    @Test
    fun `lua can store a result in a local and reuse it`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun seed() = "abc"
        })
        h.run("""
            local s = t.seed()
            capture.record(s)
            capture.record(s .. s)
        """)
        assertEquals(listOf("abc", "abcabc"), h.output)
    }

    @Test
    fun `lua can wrap a native function in its own function`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun shout(s: String) = s.uppercase()
        })
        h.run("""
            local function loud(x) return t.shout(x) end
            capture.record(loud("hey"))
        """)
        assertEquals(listOf("HEY"), h.output)
    }

    // -- natives side by side --

    @Test
    fun `natives with the same function name do not interfere`() {
        h.register(object : TwineNative("a") {
            @TwineFunction fun who() = "a"
        })
        h.register(object : TwineNative("b") {
            @TwineFunction fun who() = "b"
        })
        h.run("""
            capture.record(a.who())
            capture.record(b.who())
        """)
        assertEquals(listOf("a", "b"), h.output)
    }

    // -- failures --

    @Test
    fun `calling an unknown function fails`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun real() = "x"
        })
        h.failure("t.doesNotExist()")
        assertEquals(emptyList(), h.output)
    }

    @Test
    fun `calling an unknown native fails`() {
        h.failure("missing.anything()")
    }

    @Test
    fun `unannotated function is not exposed`() {
        h.register(object : TwineNative("t") {
            @Suppress("unused") fun hidden() = "secret"
            @TwineFunction fun visible() = "public"
        })
        h.failure("t.hidden()")
        h.run("capture.record(t.visible())")
        assertEquals(listOf("public"), h.output)
    }

    @Test
    fun `exception thrown by a function fails the script and keeps its message`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun boom(): String = error("kaboom")
        })
        val failure = h.failure("t.boom()")
        assertTrue(failure.fullMessage().contains("kaboom"), failure.fullMessage())
    }

    @Test
    fun `script stops at the first failing call`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun boom(): String = error("stop")
        })
        h.failure("""
            capture.record("before")
            t.boom()
            capture.record("after")
        """)
        assertEquals(listOf("before"), h.output)
    }

    @Test
    fun `pcall catches an exception thrown by a function`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun boom(): String = error("stop")
        })
        h.run("""
            local ok = pcall(function() t.boom() end)
            capture.record(tostring(ok))
        """)
        assertEquals(listOf("false"), h.output)
    }

    @Test
    fun `syntax error in script fails`() {
        h.failure("this is not valid luau (((")
    }
}