package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TwinePropertyTest {
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

    // BASIC TYPES

    @Test
    fun `reads String property`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val name: String = "hello"
        })
        engine.run("capture.record(t.name)")
        assertEquals(listOf("hello"), output)
    }

    @Test
    fun `reads Int property`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val count: Int = 42
        })
        engine.run("capture.record(tostring(t.count))")
        assertEquals(listOf("42"), output)
    }

    @Test
    fun `reads Double property`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val ratio: Double = 3.14
        })
        engine.run("capture.record(tostring(t.ratio))")
        assertEquals(listOf("3.14"), output)
    }

    @Test
    fun `reads Boolean property`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val flag: Boolean = true
        })
        engine.run("capture.record(tostring(t.flag))")
        assertEquals(listOf("true"), output)
    }

    // CUSTOM NAME

    @Test
    fun `reads property with custom name`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty("myCustomName")
            val internalName: String = "custom"
        })
        engine.run("capture.record(t.myCustomName)")
        assertEquals(listOf("custom"), output)
    }

    @Test
    fun `original name is not accessible when custom name set`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty("myCustomName")
            val internalName: String = "custom"
        })
        engine.run("capture.record(tostring(t.internalName))")
        assertEquals(listOf("nil"), output)
    }

    // TWINENATIVES

    @Test
    fun `reads TwineNative property and can call methods on it`() {
        val inner = object : TwineNative("inner") {
            @TwineFunction
            fun value() = "from inner"
        }
        engine.register(inner)
        engine.register(object : TwineNative("outer") {
            @TwineProperty
            val child: TwineNative = inner
        })
        engine.run("capture.record(outer.child.value())")
        assertEquals(listOf("from inner"), output)
    }

    // DYNAMIC VALUES

    @Test
    fun `reads dynamic property value at call time`() {
        val native = object : TwineNative("t") {
            @TwineProperty
            var count: Int = 0
        }
        engine.register(native)
        native.count = 99
        engine.run("capture.record(tostring(t.count))")
        assertEquals(listOf("99"), output)
    }

    // COEXISTENCE WITH FUNCTIONS

    @Test
    fun `properties and functions work on same native`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val prefix: String = "hello"

            @TwineFunction
            fun greet(name: String) = "$prefix $name"
        })
        engine.run("""
            capture.record(t.prefix)
            capture.record(t.greet("world"))
        """.trimIndent())
        assertEquals(listOf("hello", "hello world"), output)
    }

    // UNKNOWN

    @Test
    fun `returns nil for unknown property`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val name: String = "hello"
        })
        engine.run("capture.record(tostring(t.doesNotExist))")
        assertEquals(listOf("nil"), output)
    }

    // MULTIPLE PROPERTIES

    @Test
    fun `reads multiple properties independently`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val first: String = "one"
            @TwineProperty
            val second: String = "two"
            @TwineProperty
            val third: String = "three"
        })
        engine.run("""
            capture.record(t.first)
            capture.record(t.second)
            capture.record(t.third)
        """.trimIndent())
        assertEquals(listOf("one", "two", "three"), output)
    }

    @Test
    fun `unannotated property is not accessible`() {
        engine.register(object : TwineNative("t") {
            val hidden: String = "secret"
            @TwineProperty
            val visible: String = "public"
        })
        engine.run("""
            capture.record(tostring(t.hidden))
            capture.record(t.visible)
        """.trimIndent())
        assertEquals(listOf("nil", "public"), output)
    }

    @Test
    fun `function takes priority over property with same name`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val test: String = "i am a property"

            @TwineFunction
            fun test(): String = "i am a function"
        })
        engine.run("capture.record(t.test())")
        assertEquals(listOf("i am a function"), output)
    }

    @Test
    fun `property is shadowed when function has same name`() {
        engine.register(object : TwineNative("t") {
            @TwineProperty
            val test: String = "i am a property"

            @TwineFunction
            fun test(): String = "i am a function"
        })
        engine.run("capture.record(tostring(type(t.test)))")
        assertEquals(listOf("function"), output)
    }
}