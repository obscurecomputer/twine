package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TwineCallbackTest {
    private lateinit var engine: TwineEngine
    private val output = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        engine = TwineEngine()
        engine.register(object : TwineNative("capture") {
            @TwineFunction
            fun record(value: String) { output.add(value) }
        })
    }

    @AfterEach
    fun teardown() {
        output.clear()
        engine.close()
    }

    @Test
    fun `captures and invokes Lua callback`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) {
                callback = cb
            }
        })

        engine.run("""
            t.set(function()
                capture.record("called")
            end)
        """.trimIndent())

        callback.invoke()

        assertEquals(listOf("called"), output)
    }

    @Test
    fun `callback receives arguments`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) {
                callback = cb
            }
        })

        engine.run("""
            t.set(function(a, b)
                capture.record(a .. b)
            end)
        """.trimIndent())

        callback.invoke("hello ", "world")

        assertEquals(listOf("hello world"), output)
    }

    @Test
    fun `multiple callback invocations work`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) {
                callback = cb
            }
        })

        engine.run("""
            t.set(function(x)
                capture.record(tostring(x))
            end)
        """.trimIndent())

        callback.invoke(1)
        callback.invoke(2)
        callback.invoke(3)

        assertEquals(listOf("1", "2", "3"), output)
    }

    @Test
    fun `released callback no longer callable`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) {
                callback = cb
            }
        })

        engine.run("""
            t.set(function()
                capture.record("should not run")
            end)
        """.trimIndent())

        callback.release()

        try {
            callback.invoke()
        } catch (_: Exception) {
            return // expected
        }

        error("Expected failure after release")
    }

    @Test
    fun `callback can call back into engine safely`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) {
                callback = cb
            }

            @TwineFunction
            fun ping() = "pong"
        })

        engine.run("""
            t.set(function()
                capture.record(t.ping())
            end)
        """.trimIndent())

        callback.invoke()

        assertEquals(listOf("pong"), output)
    }

    @Test
    fun `call returns single value`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) { callback = cb }
        })

        engine.run("""
            t.set(function()
                return 42.0
            end)
        """.trimIndent())

        val result = callback.call<Double>()
        assertEquals(42.0, result)
    }

    @Test
    fun `call returns string value`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) { callback = cb }
        })

        engine.run("""
            t.set(function()
                return "hello"
            end)
        """.trimIndent())

        val result = callback.call<String>()
        assertEquals("hello", result)
    }

    @Test
    fun `call returns computed value from argument`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) { callback = cb }
        })

        engine.run("""
            t.set(function(x)
                return x * 2
            end)
        """.trimIndent())

        val result = callback.call<Double>(3.0)
        assertEquals(6.0, result)
    }

    @Test
    fun `call returns null for no return value`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) { callback = cb }
        })

        engine.run("""
            t.set(function() end)
        """.trimIndent())

        val result = callback.call<Any?>()
        assertEquals(null, result)
    }

    @Test
    fun `callMulti returns multiple values`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) { callback = cb }
        })

        engine.run("""
            t.set(function()
                return "a", 5.0
            end)
        """.trimIndent())

        val results = callback.call()
        assertEquals(2, results.size)
        assertEquals("a", results[0])
        assertEquals(5.0, results[1])
    }

    @Test
    fun `callMulti returns empty list for no return values`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) { callback = cb }
        })

        engine.run("""
            t.set(function() end)
        """.trimIndent())

        val results = callback.call()
        assertEquals(emptyList(), results)
    }

    @Test
    fun `callMulti returns mixed types`() {
        lateinit var callback: LuaCallback

        engine.register(object : TwineNative("t") {
            @TwineFunction
            fun set(cb: LuaCallback) { callback = cb }
        })

        engine.run("""
            t.set(function()
                return "name", 99.0, true
            end)
        """.trimIndent())

        val results = callback.call()
        assertEquals(3, results.size)
        assertEquals("name", results[0])
        assertEquals(99.0, results[1])
        assertEquals(true, results[2])
    }
}