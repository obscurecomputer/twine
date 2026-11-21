package computer.obscure.twine.tests

import computer.obscure.twine.TwineHarness
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.fullMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwineCallbackTest {
    private lateinit var h: TwineHarness

    @BeforeEach
    fun setup() {
        h = TwineHarness()
    }

    @Test
    fun `callback with no arguments`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun call0(f: () -> String) = "got:${f()}"
        })
        h.run("""capture.record(t.call0(function() return "hi" end))""")
        assertEquals(listOf("got:hi"), h.output)
    }

    @Test
    fun `callback with one argument`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun call1(f: (Int) -> String) = f(21)
        })
        h.run("""capture.record(t.call1(function(n) return "n=" .. n end))""")
        assertEquals(listOf("n=21"), h.output)
    }

    @Test
    fun `callback with two arguments`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun call2(f: (String, Int) -> String) = f("a", 2)
        })
        h.run("""capture.record(t.call2(function(s, n) return s .. n end))""")
        assertEquals(listOf("a2"), h.output)
    }

    @Test
    fun `callback with three arguments`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun call3(f: (Int, Int, Int) -> Int) = "sum:${f(1, 2, 3)}"
        })
        h.run("""capture.record(t.call3(function(a, b, c) return a + b + c end))""")
        assertEquals(listOf("sum:6"), h.output)
    }

    // -- argument and return types --

    @Test
    fun `callback receives a boolean`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun flag(f: (Boolean) -> String) = f(true)
        })
        h.run("capture.record(t.flag(function(b) return tostring(b) end))")
        assertEquals(listOf("true"), h.output)
    }

    @Test
    fun `callback can return a boolean`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun check(f: (Int) -> Boolean) = "${f(1)},${f(2)}"
        })
        h.run("capture.record(t.check(function(n) return n % 2 == 0 end))")
        assertEquals(listOf("false,true"), h.output)
    }

    @Test
    fun `callback can return a double`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun half(f: () -> Double) = "half:${f() / 2}"
        })
        h.run("capture.record(t.half(function() return 3 end))")
        assertEquals(listOf("half:1.5"), h.output)
    }

    @Test
    fun `callback return value reaches the script`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun twice(f: (Int) -> Int): Int = f(f(1))
        })
        h.run("capture.record(tostring(t.twice(function(n) return n + 10 end)))")
        assertEquals(listOf("21"), h.output)
    }

    // -- Unit callbacks and side effects --

    @Test
    fun `unit callback runs its side effects`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun each(f: (String) -> Unit) {
                f("a")
                f("b")
            }
        })
        h.run("t.each(function(s) capture.record(s) end)")
        assertEquals(listOf("a", "b"), h.output)
    }

    @Test
    fun `callback is called once per invocation, in order`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun times(n: Int, f: (Int) -> Unit) {
                for (i in 1..n) f(i)
            }
        })
        h.run("t.times(4, function(i) capture.record(tostring(i)) end)")
        assertEquals(listOf("1", "2", "3", "4"), h.output)
    }

    @Test
    fun `callback can capture script variables`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun each(f: (Int) -> Unit) {
                f(1)
                f(2)
            }
        })
        h.run("""
            local total = 0
            t.each(function(n) total = total + n end)
            capture.record(tostring(total))
        """)
        assertEquals(listOf("3"), h.output)
    }

    @Test
    fun `callback can call back into natives`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun go(f: () -> Unit) = f()
            @TwineFunction fun name() = "inner"
        })
        h.run("t.go(function() capture.record(t.name()) end)")
        assertEquals(listOf("inner"), h.output)
    }

    @Test
    fun `callbacks can be nested`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun outer(f: () -> Unit) = f()
            @TwineFunction fun inner(f: () -> Unit) = f()
        })
        h.run("""
            t.outer(function()
                capture.record("outer")
                t.inner(function() capture.record("inner") end)
            end)
        """)
        assertEquals(listOf("outer", "inner"), h.output)
    }

    // -- stored callbacks --

    @Test
    fun `stored callback can be called later in the same script`() {
        h.register(object : TwineNative("t") {
            var saved: ((String) -> String)? = null

            @TwineFunction fun store(f: (String) -> String) { saved = f }
            @TwineFunction fun fire(arg: String) = saved!!(arg)
        })
        h.run("""
            t.store(function(s) return "<" .. s .. ">" end)
            capture.record(t.fire("x"))
            capture.record(t.fire("y"))
        """)
        assertEquals(listOf("<x>", "<y>"), h.output)
    }

    @Test
    fun `stored callback survives into a later script`() {
        h.register(object : TwineNative("t") {
            var saved: (() -> String)? = null

            @TwineFunction fun store(f: () -> String) { saved = f }
            @TwineFunction fun fire() = saved!!()
        })
        h.run("""t.store(function() return "still here" end)""")
        h.run("capture.record(t.fire())")
        assertEquals(listOf("still here"), h.output)
    }

    // -- nullable callbacks --

    @Test
    fun `nullable callback can be nil`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun maybe(f: ((String) -> String)?) = f?.invoke("x") ?: "none"
        })
        h.run("capture.record(t.maybe(nil))")
        assertEquals(listOf("none"), h.output)
    }

    @Test
    fun `nullable callback can be provided`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun maybe(f: ((String) -> String)?) = f?.invoke("x") ?: "none"
        })
        h.run("""capture.record(t.maybe(function(s) return s .. "!" end))""")
        assertEquals(listOf("x!"), h.output)
    }

    // -- overloads --

    @Test
    fun `resolves between a callback and a string`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun go(v: String) = "string:$v"
            @TwineFunction fun go(f: () -> String) = "function:${f()}"
        })
        h.run("""
            capture.record(t.go("a"))
            capture.record(t.go(function() return "b" end))
        """)
        assertEquals(listOf("string:a", "function:b"), h.output)
    }

    @Test
    fun `callback works with colon calls and extra arguments`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun with(prefix: String, f: (String) -> String) = prefix + f("body")
        })
        h.run("""capture.record(t:with("> ", function(s) return s:len() .. s end))""")
        assertEquals(listOf("> 4body"), h.output)
    }

    // -- failures --

    @Test
    fun `a non-function argument does not match a callback parameter`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun call0(f: () -> String) = f()
        })
        h.failure("t.call0(5)")
        h.failure("""t.call0("nope")""")
        h.failure("t.call0({})")
    }

    @Test
    fun `nil does not match a non-nullable callback parameter`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun call0(f: () -> String) = f()
        })
        h.failure("t.call0(nil)")
    }

    @Test
    fun `a callback with too many parameters is rejected`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun big(f: (Int, Int, Int, Int) -> Int) = f(1, 2, 3, 4)
        })
        val failure = h.failure("t.big(function(a, b, c, d) return a end)")
        assertTrue(failure.fullMessage().contains("arity"), failure.fullMessage())
    }

    @Test
    fun `an error inside a callback fails the script`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun call0(f: () -> String) = f()
        })
        h.failure("""t.call0(function() error("boom") end)""")
    }

    @Test
    fun `an error inside a callback does not break later scripts`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun call0(f: () -> String) = f()
        })
        h.failure("""t.call0(function() error("boom") end)""")
        h.run("""capture.record(t.call0(function() return "fine" end))""")
        assertEquals(listOf("fine"), h.output)
    }

    @Test
    fun `a callback returning the wrong type fails the script`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun num(f: () -> Int) = f()
        })
        h.failure("""t.num(function() return "not a number" end)""")
    }
}