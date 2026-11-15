package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Arguments crossing from Luau into Kotlin must match the declared Kotlin
 * parameter type exactly: no Luau coercion, no truthiness, and nil only
 * where the parameter is nullable.
 */
class TwineArgumentTypeTest {
    private lateinit var engine: TwineEngine
    private val received = mutableListOf<Any?>()

    interface Shape
    class Circle : TwineNative(), Shape
    class Label : TwineNative()

    @Suppress("unused")
    inner class Sink : TwineNative("t") {
        @TwineFunction fun str(v: String) { received += v }
        @TwineFunction fun optStr(v: String?) { received += v }
        @TwineFunction fun num(v: Double) { received += v }
        @TwineFunction fun int(v: Int) { received += v }
        @TwineFunction fun long(v: Long) { received += v }
        @TwineFunction fun bool(v: Boolean) { received += v }
        @TwineFunction fun circle(v: Circle) { received += v }
        @TwineFunction fun shape(v: Shape) { received += v }
        @TwineFunction fun makeCircle(): Circle = Circle()
        @TwineFunction fun makeLabel(): Label = Label()

        @TwineProperty var flag: Boolean = false
    }

    @BeforeEach
    fun setup() {
        engine = TwineEngine()
        engine.register(Sink())
    }

    @AfterEach
    fun teardown() {
        received.clear()
        engine.close()
    }

    /**
     * Asserts the script fails with a [TwineError] whose message contains every fragment.
     * The Luau VM surfaces Kotlin exceptions as a LuaError carrying the original
     * exception's class and message as text, so match on that.
     */
    private fun assertTypeError(script: String, vararg fragments: String) {
        val message = try {
            engine.run(script)
            fail("expected a type error, but '$script' succeeded (received=$received)")
        } catch (e: Throwable) {
            e.message.orEmpty()
        }
        assertTrue(
            message.contains(TwineError::class.qualifiedName!!),
            "expected TwineError for '$script', got: $message"
        )
        for (fragment in fragments) {
            assertTrue(
                message.contains(fragment),
                "error for '$script' should mention '$fragment', was: $message"
            )
        }
        assertEquals(emptyList(), received, "function must not run on a type error")
    }

    // -- strings --

    @Test fun `string param accepts string`() {
        engine.run("t.str('hi')")
        assertEquals(listOf<Any?>("hi"), received)
    }

    @Test fun `string param rejects number instead of coercing`() =
        assertTypeError("t.str(123)", "str", "string", "number")

    @Test fun `string param rejects nil`() =
        assertTypeError("t.str(nil)", "str", "string", "nil")

    @Test fun `nullable string param accepts nil`() {
        engine.run("t.optStr(nil)")
        assertEquals(listOf<Any?>(null), received)
    }

    // -- numbers --

    @Test fun `double param rejects numeric string`() =
        assertTypeError("t.num('5')", "num", "number", "string")

    @Test fun `int param accepts integral number`() {
        engine.run("t.int(7)")
        assertEquals(listOf<Any?>(7), received)
    }

    @Test fun `int param rejects fractional number`() =
        assertTypeError("t.int(1.5)", "int", "integer")

    @Test fun `long param accepts integral number`() {
        engine.run("t.long(7)")
        assertEquals(listOf<Any?>(7L), received)
    }

    // -- booleans --

    @Test fun `boolean param rejects truthy string`() =
        assertTypeError("t.bool('no')", "bool", "boolean", "string")

    @Test fun `boolean param rejects table`() =
        assertTypeError("t.bool({})", "bool", "boolean", "table")

    @Test fun `boolean property rejects truthy string`() {
        assertTypeError("t.flag = 'no'", "flag", "boolean", "string")
    }

    // -- natives --

    @Test fun `native param accepts matching native`() {
        engine.run("t.circle(t.makeCircle())")
        assertTrue(received.single() is Circle)
    }

    @Test fun `native param rejects a different native`() =
        assertTypeError("t.circle(t.makeLabel())", "circle", "Circle", "Label")

    @Test fun `native param rejects plain table`() =
        assertTypeError("t.circle({})", "circle", "Circle", "table")

    @Test fun `interface param accepts implementing native`() {
        engine.run("t.shape(t.makeCircle())")
        assertTrue(received.single() is Circle)
    }

    // -- overloads --

    @Test fun `nil selects the nullable overload`() {
        engine.register(object : TwineNative("o") {
            @TwineFunction fun pick(v: String) = "string"
            @TwineFunction fun pick(v: Boolean?) = "nullable"
        })
        engine.register(object : TwineNative("out") {
            @TwineFunction fun put(v: String) { received += v }
        })
        engine.run("out.put(o.pick(nil))")
        assertEquals(listOf<Any?>("nullable"), received)
    }

    @Test fun `optional nullable trailing param can be omitted`() {
        engine.run("t.optStr()")
        assertNull(received.single())
    }
}
