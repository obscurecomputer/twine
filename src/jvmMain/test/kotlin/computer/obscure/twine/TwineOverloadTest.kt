package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwineOverloadTest {
    private lateinit var engine: TwineEngine
    private val output = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        engine = TwineEngine()
        engine.register(object : TwineNative("capture") {
            @TwineFunction
            fun record(value: Any?) { output.add(value.toString()) }
        })
    }

    @AfterEach
    fun teardown() {
        output.clear()
        engine.close()
    }

    // DIFFERENT ARGUMENT COUNTS
    @Test
    fun `resolves by argument count`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun foo() = "zero"

            @TwineFunction
            fun foo(a: String) = "one"

            @TwineFunction
            fun foo(a: String, b: String) = "two"
        })

        engine.run("""
            capture.record(t.foo())
            capture.record(t.foo("a"))
            capture.record(t.foo("a", "b"))
        """.trimIndent())

        assertEquals(listOf("zero", "one", "two"), output)
    }

    // DIFFERENT TYPES (SAME COUNT)
    @Test
    fun `resolves by argument types`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun bar(v: String) = "string"

            @TwineFunction
            fun bar(v: Double) = "number"

            @TwineFunction
            fun bar(v: Boolean) = "boolean"
        })

        engine.run("""
            capture.record(t.bar("hello"))
            capture.record(t.bar(123))
            capture.record(t.bar(true))
        """.trimIndent())

        assertEquals(listOf("string", "number", "boolean"), output)
    }

    // COMPLEX OVERLOADS (RGB example)
    @Test
    fun `resolves complex mixed overloads`() {
        engine.register(object : TwineNative("color") {
            @TwineFunction
            fun rgb(hex: String) = "hex:$hex"

            @TwineFunction
            fun rgb(r: Int, g: Int, b: Int) = "rgb:$r,$g,$b"
        })

        engine.run("""
            capture.record(color.rgb("#FF0000"))
            capture.record(color.rgb(255, 0, 0))
        """.trimIndent())

        assertEquals(listOf("hex:#FF0000", "rgb:255,0,0"), output)
    }

    // EXPECTING ERRORS (THE DESCRIPTIVE ONES)
    @Test
    fun `throws descriptive error on no match`() {
        engine.register(object : TwineNative("math") {
            @TwineFunction
            fun add(a: Double, b: Double) = a + b
            @TwineFunction
            fun add(a: String, b: String) = a + b
        })

        val result = engine.runSafe("math.add('hello', 5)")

        assertTrue(result.isFailure, "Result should be a failure")
        val error = result.exceptionOrNull() as TwineError

        assertTrue(error.message!!.contains("No matching overload found for: add(String, Number)"), "Should show attempt")
        assertTrue(error.message!!.contains("Available overloads for \"add\""), "Should show available options")
        assertTrue(error.message!!.contains("(a: Double, b: Double): Double"), "Should show signature")
    }

    // VARARGS OVERLOAD
    @Test
    fun `resolves vararg vs fixed arguments`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun log(msg: String) = "fixed:$msg"

            @TwineFunction
            fun log(vararg items: Any?) = "vararg:${items.size}"
        })

        engine.run("""
            capture.record(t.log("only one"))
            capture.record(t.log("one", 2, true))
        """.trimIndent())

        assertEquals(listOf("fixed:only one", "vararg:3"), output)
    }

    // AMBIGUITY (NULL/NIL)
    @Test
    fun `handles nil arguments in overloads`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun check(s: String) = "string"
            
            @TwineFunction
            fun check(n: Double) = "number"
        })

        val result = engine.runSafe("t.check(nil)")
        assertTrue(result.isFailure)
    }

    // NULLABLE / OPTIONAL TRAILING PARAMS
    @Test
    fun `resolves overload with trailing nullable param omitted`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun fov(to: Float, duration: Double, easing: String, onFinish: LuaCallback?) = "fov:$to,$duration,$easing,${onFinish == null}"
        })

        engine.run("""
        capture.record(t.fov(90, 1.0, "linear"))
    """.trimIndent())

        assertEquals(listOf("fov:90.0,1.0,linear,true"), output)
    }

    @Test
    fun `resolves overload with trailing nullable param as nil`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun fov(to: Float, duration: Double, easing: String, onFinish: LuaCallback?) = "fov:${onFinish == null}"
        })

        engine.run("""
        capture.record(t.fov(90, 1.0, "linear", nil))
    """.trimIndent())

        assertEquals(listOf("fov:true"), output)
    }

    @Test
    fun `resolves overload with trailing nullable param provided`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun fov(to: Float, duration: Double, easing: String, onFinish: LuaCallback?) = "fov:${onFinish != null}"
        })

        engine.run("""
        capture.record(t.fov(90, 1.0, "linear", function() end))
    """.trimIndent())

        assertEquals(listOf("fov:true"), output)
    }

    @Test
    fun `prefers non-nullable overload over nullable when both match`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun action(a: String) = "required"

            @TwineFunction
            fun action(a: String, b: String?) = "nullable"
        })

        engine.run("""
        capture.record(t.action("hello"))
    """.trimIndent())

        // exact match (1 arg) should win over nullable with missing arg
        assertEquals(listOf("required"), output)
    }

    @Test
    fun `throws on missing required argument`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun move(x: Double, y: Double) = "$x,$y"
        })

        val result = engine.runSafe("t.move(1.0)")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as TwineError
        assertTrue(error.message!!.contains("No matching overload found for: move(Number)"))
    }

    @Test
    fun `multiple trailing nullables all omitted`() {
        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun animate(duration: Double, easing: String?, onStart: LuaCallback?, onFinish: LuaCallback?) =
                "animate:$duration,${easing == null},${onStart == null},${onFinish == null}"
        })

        engine.run("""
        capture.record(t.animate(2.0))
    """.trimIndent())

        assertEquals(listOf("animate:2.0,true,true,true"), output)
    }
}