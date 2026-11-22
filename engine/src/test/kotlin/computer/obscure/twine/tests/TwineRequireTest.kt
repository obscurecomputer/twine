package computer.obscure.twine.tests

import computer.obscure.twine.TwineHarness
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.fullMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwineRequireTest {
    private lateinit var h: TwineHarness

    @BeforeEach
    fun setup() {
        h = TwineHarness()
    }

    // -- basics --

    @Test
    fun `returns the table a module returns`() {
        h.module("m", """return { value = "hello" }""")
        h.run("""
            local m = require("m")
            capture.record(m.value)
        """)
        assertEquals(listOf("hello"), h.output)
    }

    @Test
    fun `functions on a required module can be called`() {
        h.module("math2", """
            local M = {}
            function M.double(x) return x * 2 end
            return M
        """)
        h.run("""
            local math2 = require("math2")
            capture.record(tostring(math2.double(21)))
        """)
        assertEquals(listOf("42"), h.output)
    }

    @Test
    fun `a module can return a function`() {
        h.module("greet", """return function(name) return "hi " .. name end""")
        h.run("""
            local greet = require("greet")
            capture.record(greet("twine"))
        """)
        assertEquals(listOf("hi twine"), h.output)
    }

    @Test
    fun `a module can return a non-table value`() {
        h.module("answer", "return 42")
        h.module("label", """return "text" """)
        h.run("""
            capture.record(tostring(require("answer")))
            capture.record(require("label"))
        """)
        assertEquals(listOf("42", "text"), h.output)
    }

    @Test
    fun `a module that returns nothing evaluates to true`() {
        h.module("sideeffect", """capture.record("ran")""")
        h.run("""capture.record(tostring(require("sideeffect")))""")
        assertEquals(listOf("ran", "true"), h.output)
    }

    @Test
    fun `module names are passed to the loader verbatim`() {
        h.module("lib/util.helpers", """return { ok = "yes" }""")
        h.run("""capture.record(require("lib/util.helpers").ok)""")
        assertEquals(listOf("yes"), h.output)
    }

    // -- caching --

    @Test
    fun `requiring twice returns the same table`() {
        h.module("m", "return {}")
        h.run("""
            local a = require("m")
            local b = require("m")
            capture.record(tostring(rawequal(a, b)))
        """)
        assertEquals(listOf("true"), h.output)
    }

    @Test
    fun `a module body runs only once`() {
        h.module("once", """
            capture.record("loaded")
            return {}
        """)
        h.run("""
            require("once")
            require("once")
            require("once")
        """)
        assertEquals(listOf("loaded"), h.output)
    }

    @Test
    fun `a module that returns nothing also only runs once`() {
        h.module("quiet", """capture.record("loaded")""")
        h.run("""
            require("quiet")
            require("quiet")
        """)
        assertEquals(listOf("loaded"), h.output)
    }

    @Test
    fun `mutations to a module are visible to later requires`() {
        h.module("shared", """return { value = "original" }""")
        h.run("""
            local a = require("shared")
            a.value = "changed"
            capture.record(require("shared").value)
        """)
        assertEquals(listOf("changed"), h.output)
    }

    @Test
    fun `module local state persists between requires`() {
        h.module("counter", """
            local n = 0
            return { inc = function() n += 1; return n end }
        """)
        h.run("""
            local a = require("counter")
            local b = require("counter")
            capture.record(tostring(a.inc()))
            capture.record(tostring(b.inc()))
            capture.record(tostring(a.inc()))
        """)
        assertEquals(listOf("1", "2", "3"), h.output)
    }

    @Test
    fun `different modules are independent`() {
        h.module("a", """return { name = "a" }""")
        h.module("b", """return { name = "b" }""")
        h.run("""
            capture.record(require("a").name)
            capture.record(require("b").name)
        """)
        assertEquals(listOf("a", "b"), h.output)
    }

    @Test
    fun `a module is shared between separate script runs`() {
        h.module("shared", """return { value = "original" }""")
        h.run("""require("shared").value = "from first script" """)
        h.run("""capture.record(require("shared").value)""")
        assertEquals(listOf("from first script"), h.output)
    }

    @Test
    fun `a module body runs once across separate script runs`() {
        h.module("once", """
            capture.record("loaded")
            return {}
        """)
        h.run("""require("once")""")
        h.run("""require("once")""")
        assertEquals(listOf("loaded"), h.output)
    }

    // -- nesting --

    @Test
    fun `a module can require another module`() {
        h.module("inner", """return { value = "inner value" }""")
        h.module("outer", """
            local inner = require("inner")
            return { value = "outer + " .. inner.value }
        """)
        h.run("""capture.record(require("outer").value)""")
        assertEquals(listOf("outer + inner value"), h.output)
    }

    @Test
    fun `dependencies load before the module that needs them`() {
        h.module("dep", """
            capture.record("dep")
            return {}
        """)
        h.module("main", """
            capture.record("main start")
            require("dep")
            capture.record("main end")
            return {}
        """)
        h.run("""require("main")""")
        assertEquals(listOf("main start", "dep", "main end"), h.output)
    }

    @Test
    fun `a shared dependency is only loaded once`() {
        h.module("base", """
            capture.record("base")
            return { id = "base" }
        """)
        h.module("left", """return { base = require("base") }""")
        h.module("right", """return { base = require("base") }""")
        h.run("""
            local left = require("left")
            local right = require("right")
            capture.record(tostring(rawequal(left.base, right.base)))
        """)
        assertEquals(listOf("base", "true"), h.output)
    }

    @Test
    fun `deeply nested requires resolve`() {
        h.module("c", """return { value = "c" }""")
        h.module("b", """return { value = "b" .. require("c").value }""")
        h.module("a", """return { value = "a" .. require("b").value }""")
        h.run("""capture.record(require("a").value)""")
        assertEquals(listOf("abc"), h.output)
    }

    // -- circular requires --

    @Test
    fun `a module requiring itself fails`() {
        h.module("selfish", """return require("selfish")""")
        val failure = h.failure("""require("selfish")""")
        assertTrue(failure.fullMessage().contains("Circular"), failure.fullMessage())
    }

    @Test
    fun `circular requires between two modules fail`() {
        h.module("ping", """return { other = require("pong") }""")
        h.module("pong", """return { other = require("ping") }""")
        val failure = h.failure("""require("ping")""")
        assertTrue(failure.fullMessage().contains("Circular"), failure.fullMessage())
    }

    // -- errors --

    @Test
    fun `fails when the module does not exist`() {
        val failure = h.failure("""require("missing")""")
        assertTrue(failure.fullMessage().contains("missing"), failure.fullMessage())
    }

    @Test
    fun `fails when called without a module name`() {
        h.failure("require()")
    }

    @Test
    fun `a module with a syntax error fails and names the module`() {
        h.module("broken", "local = = =")
        val failure = h.failure("""require("broken")""")
        assertTrue(failure.fullMessage().contains("broken"), failure.fullMessage())
    }

    @Test
    fun `a runtime error inside a module propagates`() {
        h.module("explodes", """error("module blew up")""")
        val failure = h.failure("""require("explodes")""")
        assertTrue(failure.fullMessage().contains("module blew up"), failure.fullMessage())
    }

    @Test
    fun `a failed require is not cached and can be retried`() {
        h.module("flaky", """error("not yet")""")
        h.failure("""require("flaky")""")

        h.module("flaky", """return { value = "fixed" }""")
        h.run("""capture.record(require("flaky").value)""")
        assertEquals(listOf("fixed"), h.output)
    }

    @Test
    fun `a failed circular require does not poison later requires`() {
        h.module("loop", """return require("loop")""")
        h.failure("""require("loop")""")

        h.module("loop", """return { value = "ok" }""")
        h.run("""capture.record(require("loop").value)""")
        assertEquals(listOf("ok"), h.output)
    }

    @Test
    fun `code after a failed pcall require still runs`() {
        h.run("""
            local ok = pcall(require, "missing")
            capture.record(tostring(ok))
        """)
        assertEquals(listOf("false"), h.output)
    }

    // -- natives inside modules --

    @Test
    fun `modules can use registered natives`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun shout(s: String) = s.uppercase()
        })
        h.module("loud", """
            return { say = function(s) return t.shout(s) end }
        """)
        h.run("""capture.record(require("loud").say("hello"))""")
        assertEquals(listOf("HELLO"), h.output)
    }

    @Test
    fun `modules can use natives at load time`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun value() = "from native"
        })
        h.module("eager", """return { value = t.value() }""")
        h.run("""capture.record(require("eager").value)""")
        assertEquals(listOf("from native"), h.output)
    }

    @Test
    fun `a module function can be passed to a native as a callback`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun apply(cb: (String) -> String) = cb("x")
        })
        h.module("cb", """return function(s) return s .. "!" end""")
        h.run("""capture.record(t.apply(require("cb")))""")
        assertEquals(listOf("x!"), h.output)
    }

    @Test
    fun `natives are still usable after a module has been required`() {
        h.register(object : TwineNative("t") {
            @TwineFunction fun ping() = "pong"
        })
        h.module("m", "return {}")
        h.run("""
            require("m")
            capture.record(t.ping())
        """)
        assertEquals(listOf("pong"), h.output)
    }
}