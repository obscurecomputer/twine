package computer.obscure.twine.tests

import computer.obscure.twine.TwineHarness
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TwineMethodCallTest {
    private lateinit var h: TwineHarness

    class Greeter : TwineNative("greeter") {
        @TwineProperty val prefix = "hi"

        @TwineFunction fun hello(name: String): String = "$prefix $name"
        @TwineFunction fun hello(n: Int): String = "integer $n"
        @TwineFunction fun ping(): String = "pong"
    }

    class Counter(name: String) : TwineNative(name) {
        var count = 0

        @TwineFunction fun inc(): Int {
            count++
            return count
        }

        @TwineFunction fun add(n: Int): Int {
            count += n
            return count
        }
    }

    @BeforeEach
    fun setup() {
        h = TwineHarness()
        h.register(Greeter())
    }

    @Test
    fun `plain dot call`() {
        h.run("""capture.record(greeter.hello("bob"))""")
        assertEquals(listOf("hi bob"), h.output)
    }

    @Test
    fun `explicit self call`() {
        h.run("""capture.record(greeter.hello(greeter, "bob"))""")
        assertEquals(listOf("hi bob"), h.output)
    }

    @Test
    fun `colon call`() {
        h.run("""capture.record(greeter:hello("bob"))""")
        assertEquals(listOf("hi bob"), h.output)
    }

    @Test
    fun `all three call styles reach the same overload`() {
        h.run("""
            capture.record(greeter.hello(67))
            capture.record(greeter.hello(greeter, 67))
            capture.record(greeter:hello(67))
        """)
        assertEquals(List(3) { "integer 67" }, h.output)
    }

    @Test
    fun `colon call with no arguments`() {
        h.run("capture.record(greeter:ping())")
        assertEquals(listOf("pong"), h.output)
    }

    @Test
    fun `explicit self call with no arguments`() {
        h.run("capture.record(greeter.ping(greeter))")
        assertEquals(listOf("pong"), h.output)
    }

    @Test
    fun `colon call picks the overload by argument type`() {
        h.run("""
            capture.record(greeter:hello("x"))
            capture.record(greeter:hello(1))
        """)
        assertEquals(listOf("hi x", "integer 1"), h.output)
    }

    @Test
    fun `colon call with a wrong argument type fails`() {
        h.failure("greeter:hello(true)")
    }

    @Test
    fun `plain call and colon call can be mixed in one script`() {
        h.run("""
            capture.record(greeter.ping())
            capture.record(greeter:ping())
        """)
        assertEquals(listOf("pong", "pong"), h.output)
    }

    @Test
    fun `colon call state is kept on the instance`() {
        val counter = Counter("counter")
        h.register(counter)
        h.run("""
            counter:inc()
            counter:inc()
            capture.record(tostring(counter:add(5)))
        """)
        assertEquals(listOf("7"), h.output)
        assertEquals(7, counter.count)
    }

    @Test
    fun `two instances of the same class keep separate state`() {
        val a = Counter("a")
        val b = Counter("b")
        h.register(a)
        h.register(b)
        h.run("""
            a:inc()
            a:inc()
            b:inc()
        """)
        assertEquals(2, a.count)
        assertEquals(1, b.count)
    }

    @Test
    fun `explicit self selects the instance it names`() {
        val a = Counter("a")
        val b = Counter("b")
        h.register(a)
        h.register(b)

        h.failure("a.inc(b)")
    }

    @Test
    fun `colon call on an unknown function fails`() {
        h.failure("greeter:nope()")
    }
}