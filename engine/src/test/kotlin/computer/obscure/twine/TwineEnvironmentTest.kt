package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import computer.obscure.twine.engine.TwineContext
import computer.obscure.twine.natives.TwineEnvironment
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TwineEnvironmentTest {

    private fun native(name: String, value: String) = object : TwineNative(name) {
        @TwineFunction fun value() = value
        @TwineProperty val label = value
    }

    @Test
    fun `registered natives are available to scripts`() {
        val h = TwineHarness()
        h.register(native("a", "A"))
        h.run("capture.record(a.value())")
        assertEquals(listOf("A"), h.output)
    }

    @Test
    fun `many natives can be registered in one environment`() {
        val h = TwineHarness()
        h.register(native("a", "A"))
        h.register(native("b", "B"))
        h.register(native("c", "C"))
        h.run("""
            capture.record(a.value())
            capture.record(b.value())
            capture.record(c.value())
        """)
        assertEquals(listOf("A", "B", "C"), h.output)
    }

    @Test
    fun `the same environment can run several scripts`() {
        val h = TwineHarness()
        h.register(native("a", "A"))
        h.run("capture.record(a.value())")
        h.run("capture.record(a.label)")
        h.run("capture.record(a.value())")
        assertEquals(listOf("A", "A", "A"), h.output)
    }

    @Test
    fun `a failed script does not break later scripts`() {
        val h = TwineHarness()
        h.register(native("a", "A"))
        h.failure("a.nope()")
        h.run("capture.record(a.value())")
        assertEquals(listOf("A"), h.output)
    }

    @Test
    fun `a native registered after an earlier run is available to the next run`() {
        val h = TwineHarness()
        h.register(native("a", "A"))
        h.run("capture.record(a.value())")
        h.register(native("b", "B"))
        h.run("capture.record(b.value())")
        assertEquals(listOf("A", "B"), h.output)
    }

    @Test
    fun `environments are isolated from each other`() {
        val context = TwineContext()
        val output = mutableListOf<String>()

        val first = TwineEnvironment()
        first.registry.add(Capture(output))
        first.registry.add(native("onlyFirst", "1"))

        val second = TwineEnvironment()
        second.registry.add(Capture(output))
        second.registry.add(native("onlySecond", "2"))

        context.engine.run("capture.record(onlyFirst.value())", first)

        var leaked = true
        try {
            context.engine.run("capture.record(onlyFirst.value())", second)
        } catch (e: Throwable) {
            e.printStackTrace()
            leaked = false
        }

        context.engine.run("capture.record(onlySecond.value())", second)

        assertEquals(false, leaked, "onlyFirst must not be visible from the second environment")
        assertEquals(listOf("1", "2"), output)
    }

    @Test
    fun `one context can run scripts against different environments`() {
        val context = TwineContext()
        val output = mutableListOf<String>()

        val a = TwineEnvironment().apply {
            registry.add(Capture(output))
            registry.add(native("who", "a"))
        }
        val b = TwineEnvironment().apply {
            registry.add(Capture(output))
            registry.add(native("who", "b"))
        }

        context.engine.run("capture.record(who.value())", a)
        context.engine.run("capture.record(who.value())", b)
        context.engine.run("capture.record(who.value())", a)

        assertEquals(listOf("a", "b", "a"), output)
    }

    @Test
    fun `contexts are independent of each other`() {
        val output = mutableListOf<String>()
        val env = TwineEnvironment().apply {
            registry.add(Capture(output))
            registry.add(native("a", "A"))
        }

        TwineContext().engine.run("capture.record(a.value())", env)
        TwineContext().engine.run("capture.record(a.value())", env)

        assertEquals(listOf("A", "A"), output)
    }

    @Test
    fun `the example from the docs runs end to end`() {
        class Test : TwineNative("testobj") {
            @TwineProperty val test = "hi"

            @TwineFunction fun hello(test: String): String = "string:$test"
            @TwineFunction fun hello(test: Int): String = "int:$test"
        }

        val h = TwineHarness()
        h.register(Test())
        h.run("""
            capture.record(testobj.hello("hiahahah"))
            capture.record(testobj.hello(67))
            capture.record(testobj.hello(testobj, 67))
            capture.record(testobj:hello(67))
            capture.record(testobj.test)
        """)
        assertEquals(
            listOf("string:hiahahah", "int:67", "int:67", "int:67", "hi"),
            h.output
        )
    }
}