package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwineArgumentTypeTest {
    private lateinit var h: TwineHarness
    private val received = mutableListOf<Any?>()

    interface Shape
    class Circle : TwineNative("circle"), Shape
    class Label : TwineNative("label")

    class Sink(private val received: MutableList<Any?>) : TwineNative("t") {
        @TwineFunction fun str(v: String) { received += v }
        @TwineFunction fun optStr(v: String?) { received += v }
        @TwineFunction fun num(v: Double) { received += v }
        @TwineFunction fun int(v: Int) { received += v }
        @TwineFunction fun long(v: Long) { received += v }
        @TwineFunction fun float(v: Float) { received += v }
        @TwineFunction fun bool(v: Boolean) { received += v }
        @TwineFunction fun any(v: Any) { received += v }
        @TwineFunction fun optAny(v: Any?) { received += v }
        @TwineFunction fun circle(v: Circle) { received += v }
        @TwineFunction fun shape(v: Shape) { received += v }
        @TwineFunction fun makeCircle(): Circle = Circle()
        @TwineFunction fun makeLabel(): Label = Label()
    }

    @BeforeEach
    fun setup() {
        received.clear()
        h = TwineHarness()
        h.register(Sink(received))
    }

    private fun assertRejected(script: String) {
        h.failure(script)
        assertEquals(emptyList(), received, "function must not run on a type error")
    }

    // -- strings --

    @Test
    fun `string param accepts string`() {
        h.run("t.str('hi')")
        assertEquals(listOf<Any?>("hi"), received)
    }

    @Test
    fun `string param accepts an empty string`() {
        h.run("t.str('')")
        assertEquals(listOf<Any?>(""), received)
    }

    @Test
    fun `string param rejects number instead of coercing`() = assertRejected("t.str(123)")

    @Test
    fun `string param rejects boolean`() = assertRejected("t.str(true)")

    @Test
    fun `string param rejects table`() = assertRejected("t.str({})")

    @Test
    fun `string param rejects nil`() = assertRejected("t.str(nil)")

    @Test
    fun `string param rejects a missing argument`() = assertRejected("t.str()")

    @Test
    fun `nullable string param accepts nil`() {
        h.run("t.optStr(nil)")
        assertEquals(listOf<Any?>(null), received)
    }

    @Test
    fun `nullable string param accepts a string`() {
        h.run("t.optStr('x')")
        assertEquals(listOf<Any?>("x"), received)
    }

    @Test
    fun `nullable string param still rejects a number`() = assertRejected("t.optStr(5)")

    @Test
    fun `nullable trailing param can not be omitted`() {
        h.failure("t.optStr()")
    }

    // -- numbers --

    @Test
    fun `double param accepts a number`() {
        h.run("t.num(2.5)")
        assertEquals(listOf<Any?>(2.5), received)
    }

    @Test
    fun `double param accepts an integral number`() {
        h.run("t.num(2)")
        assertEquals(listOf<Any?>(2.0), received)
    }

    @Test
    fun `double param accepts a negative number`() {
        h.run("t.num(-0.5)")
        assertEquals(listOf<Any?>(-0.5), received)
    }

    @Test
    fun `double param rejects numeric string`() = assertRejected("t.num('5')")

    @Test
    fun `double param rejects boolean`() = assertRejected("t.num(true)")

    @Test
    fun `double param rejects nil`() = assertRejected("t.num(nil)")

    @Test
    fun `int param accepts integral number`() {
        h.run("t.int(7)")
        assertEquals(listOf<Any?>(7), received)
    }

    @Test
    fun `int param accepts zero and negatives`() {
        h.run("""
            t.int(0)
            t.int(-3)
        """)
        assertEquals(listOf<Any?>(0, -3), received)
    }

    @Test
    fun `int param rejects fractional number`() = assertRejected("t.int(1.5)")

    @Test
    fun `int param rejects numeric string`() = assertRejected("t.int('7')")

    @Test
    fun `long param accepts integral number`() {
        h.run("t.long(7)")
        assertEquals(listOf<Any?>(7L), received)
    }

    @Test
    fun `long param rejects fractional number`() = assertRejected("t.long(0.25)")

    @Test
    fun `float param accepts a number`() {
        h.run("t.float(1.5)")
        assertEquals(listOf<Any?>(1.5f), received)
    }

    @Test
    fun `float param rejects a string`() = assertRejected("t.float('1.5')")

    // -- booleans --

    @Test
    fun `boolean param accepts true and false`() {
        h.run("""
            t.bool(true)
            t.bool(false)
        """)
        assertEquals(listOf<Any?>(true, false), received)
    }

    @Test
    fun `boolean param rejects truthy string`() = assertRejected("t.bool('no')")

    @Test
    fun `boolean param rejects number`() = assertRejected("t.bool(1)")

    @Test
    fun `boolean param rejects table`() = assertRejected("t.bool({})")

    @Test
    fun `boolean param rejects nil`() = assertRejected("t.bool(nil)")

    // -- Any --

    @Test
    fun `Any param receives a string`() {
        h.run("t.any('hello')")
        assertEquals(listOf<Any?>("hello"), received)
    }

    @Test
    fun `Any param receives a number as a Double`() {
        h.run("t.any(42)")
        assertEquals(listOf<Any?>(42.0), received)
    }

    @Test
    fun `Any param receives a boolean`() {
        h.run("t.any(true)")
        assertEquals(listOf<Any?>(true), received)
    }

    @Test
    fun `Any param receives a table as a Map`() {
        h.run("""t.any({ key = "value", number = 42 })""")
        val map = received.single() as Map<*, *>
        assertEquals("value", map["key"])
        assertEquals(42.0, map["number"])
    }

    @Test
    fun `Any param rejects nil`() = assertRejected("t.any(nil)")

    @Test
    fun `nullable Any param accepts nil`() {
        h.run("t.optAny(nil)")
        assertNull(received.single())
    }

    // -- natives --

    @Test
    fun `native param accepts matching native`() {
        h.run("t.circle(t.makeCircle())")
        assertTrue(received.single() is Circle)
    }

    @Test
    fun `native param rejects a different native`() = assertRejected("t.circle(t.makeLabel())")

    @Test
    fun `native param rejects plain table`() = assertRejected("t.circle({})")

    @Test
    fun `native param rejects string`() = assertRejected("t.circle('circle')")

    @Test
    fun `native param rejects nil`() = assertRejected("t.circle(nil)")

    @Test
    fun `interface param accepts implementing native`() {
        h.run("t.shape(t.makeCircle())")
        assertTrue(received.single() is Circle)
    }

    @Test
    fun `interface param rejects a native that does not implement it`() =
        assertRejected("t.shape(t.makeLabel())")

    @Test
    fun `native identity is preserved across the boundary`() {
        val circle = Circle()
        h.register(circle)
        h.run("t.circle(circle)")
        assertTrue(received.single() === circle)
    }
}