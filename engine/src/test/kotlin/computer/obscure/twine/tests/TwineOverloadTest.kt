package computer.obscure.twine.tests

import computer.obscure.twine.TwineHarness
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.fullMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwineOverloadTest {
    private lateinit var h: TwineHarness

    @BeforeEach
    fun setup() {
        h = TwineHarness()
    }

    // -- by argument count --

    @Test
    fun `resolves by argument count`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun foo() = "zero"
            @TwineFunction fun foo(a: String) = "one"
            @TwineFunction fun foo(a: String, b: String) = "two"
        })
        h.run("""
            capture.record(t.foo())
            capture.record(t.foo("a"))
            capture.record(t.foo("a", "b"))
        """)
        assertEquals(listOf("zero", "one", "two"), h.output)
    }

    @Test
    fun `resolves by argument count with colon calls`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun foo() = "zero"
            @TwineFunction fun foo(a: String) = "one"
            @TwineFunction fun foo(a: String, b: String) = "two"
        })
        h.run("""
            capture.record(t:foo())
            capture.record(t:foo("a"))
            capture.record(t:foo("a", "b"))
        """)
        assertEquals(listOf("zero", "one", "two"), h.output)
    }

    // -- by argument type --

    @Test
    fun `resolves by argument type`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun bar(v: String) = "string"
            @TwineFunction fun bar(v: Double) = "number"
            @TwineFunction fun bar(v: Boolean) = "boolean"
        })
        h.run("""
            capture.record(t.bar("hello"))
            capture.record(t.bar(123))
            capture.record(t.bar(true))
        """)
        assertEquals(listOf("string", "number", "boolean"), h.output)
    }

    @Test
    fun `resolves String vs Int`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun hello(test: String) = "string"
            @TwineFunction fun hello(test: Int) = "int"
        })
        h.run("""
            capture.record(t.hello("hiahahah"))
            capture.record(t.hello(67))
        """)
        assertEquals(listOf("string", "int"), h.output)
    }

    @Test
    fun `declaration order does not change which overload is chosen`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun pick(v: Boolean) = "boolean"
            @TwineFunction fun pick(v: Double) = "number"
            @TwineFunction fun pick(v: String) = "string"
        })
        h.run("""
            capture.record(t.pick("x"))
            capture.record(t.pick(1))
            capture.record(t.pick(false))
        """)
        assertEquals(listOf("string", "number", "boolean"), h.output)
    }

    @Test
    fun `resolves mixed count and type overloads`() {
        h.register(object : TwineNative("color") {
            @TwineFunction fun rgb(hex: String) = "hex:$hex"
            @TwineFunction fun rgb(r: Int, g: Int, b: Int) = "rgb:$r,$g,$b"
        })
        h.run("""
            capture.record(color.rgb("#FF0000"))
            capture.record(color.rgb(255, 0, 0))
        """)
        assertEquals(listOf("hex:#FF0000", "rgb:255,0,0"), h.output)
    }

    @Test
    fun `same count different types in later positions`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun mix(a: String, b: String) = "ss"
            @TwineFunction fun mix(a: String, b: Double) = "sn"
            @TwineFunction fun mix(a: Double, b: String) = "ns"
        })
        h.run("""
            capture.record(t.mix("a", "b"))
            capture.record(t.mix("a", 1))
            capture.record(t.mix(1, "b"))
        """)
        assertEquals(listOf("ss", "sn", "ns"), h.output)
    }

    // -- no match --

    @Test
    fun `fails when no overload matches the argument types`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun add(a: Double, b: Double) = a + b
            @TwineFunction fun add(a: String, b: String) = a + b
        })
        val failure = h.failure("t.add('hello', 5)")
        assertTrue(failure.fullMessage().contains("add"), failure.fullMessage())
    }

    @Test
    fun `fails when no overload accepts that many arguments`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun one(a: String) = a
        })
        h.failure("t.one('a', 'b')")
    }

    @Test
    fun `fails on a missing required argument`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun move(x: Double, y: Double) = "$x,$y"
        })
        h.failure("t.move(1.0)")
    }

    @Test
    fun `a failed overload resolution does not run any overload`() {
        var ran = false
        h.register(object : TwineNative("t") {
            @TwineFunction fun go(a: String) { ran = true }
            @TwineFunction fun go(a: Double) { ran = true }
        })
        h.failure("t.go(true)")
        assertTrue(!ran)
    }

    @Test
    fun `nil matches no non-nullable overload`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun check(s: String) = "string"
            @TwineFunction fun check(n: Double) = "number"
        })
        h.failure("t.check(nil)")
    }

    // -- varargs --

    @Test
    fun `resolves vararg vs fixed arguments`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun log(msg: String) = "fixed:$msg"
            @TwineFunction fun log(vararg items: Any?) = "vararg:${items.size}"
        })
            h.run("""
            capture.record(t.log("only one"))
            capture.record(t.log("one", 2, true))
        """)
        assertEquals(listOf("fixed:only one", "vararg:3"), h.output)
    }

    // -- nullable / trailing parameters --

    @Test
    fun `trailing nullable parameter can not be omitted`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun fov(to: Float, duration: Double, easing: String, tag: String?) =
                "fov:$to,$duration,$easing,${tag == null}"
        })
        h.failure("""capture.record(t.fov(90, 1.0, "linear"))""")
    }

    @Test
    fun `trailing nullable parameter can be nil`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun fov(to: Float, duration: Double, easing: String, tag: String?) =
                "fov:${tag == null}"
        })
        h.run("""capture.record(t.fov(90, 1.0, "linear", nil))""")
        assertEquals(listOf("fov:true"), h.output)
    }

    @Test
    fun `trailing nullable parameter can be provided`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun fov(to: Float, duration: Double, easing: String, tag: String?) =
                "fov:$tag"
        })
        h.run("""capture.record(t.fov(90, 1.0, "linear", "extra"))""")
        assertEquals(listOf("fov:extra"), h.output)
    }

    @Test
    fun `multiple trailing nullables can not all be omitted`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun animate(duration: Double, easing: String?, a: String?, b: String?) =
                "animate:$duration,${easing == null},${a == null},${b == null}"
        })
        h.failure("capture.record(t.animate(2.0))")
    }

    @Test
    fun `multiple trailing nullables can not be partly provided`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun animate(duration: Double, easing: String?, a: String?) =
                "animate:$duration,$easing,${a == null}"
        })
        h.failure("""capture.record(t.animate(2.0, "ease"))""")
    }

    @Test
    fun `required parameter before nullable ones is still required`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun animate(duration: Double, easing: String?) = "ok"
        })
        h.failure("t.animate()")
    }

    @Test
    fun `prefers the exact non-nullable overload over a nullable one with an omitted argument`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun action(a: String) = "required"
            @TwineFunction fun action(a: String, b: String?) = "nullable"
        })
        h.run("""capture.record(t.action("hello"))""")
        assertEquals(listOf("required"), h.output)
    }

    @Test
    fun `nil selects the nullable overload`() {
        h.register(object : TwineNative("o") {
            @TwineFunction fun pick(v: String) = "string"
            @TwineFunction fun pick(v: Boolean?) = "nullable"
        })
        h.run("capture.record(o.pick(nil))")
        assertEquals(listOf("nullable"), h.output)
    }
}