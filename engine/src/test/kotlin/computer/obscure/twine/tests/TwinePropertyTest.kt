package computer.obscure.twine.tests

import computer.obscure.twine.TwineHarness
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TwinePropertyTest {
    private lateinit var h: TwineHarness

    @BeforeEach
    fun setup() {
        h = TwineHarness()
    }

    // -- reading --

    @Test
    fun `reads String property`() {
        h.register(object : TwineNative("t") {
            @TwineProperty val name: String = "hello"
        })
        h.run("capture.record(t.name)")
        assertEquals(listOf("hello"), h.output)
    }

    @Test
    fun `reads Int property`() {
        h.register(object : TwineNative("t") {
            @TwineProperty val count: Int = 42
        })
        h.run("capture.record(tostring(t.count))")
        assertEquals(listOf("42"), h.output)
    }

    @Test
    fun `reads Double property`() {
        h.register(object : TwineNative("t") {
            @TwineProperty val ratio: Double = 3.14
        })
        h.run("capture.record(tostring(t.ratio))")
        assertEquals(listOf("3.14"), h.output)
    }

    @Test
    fun `reads Boolean property`() {
        h.register(object : TwineNative("t") {
            @TwineProperty val flag: Boolean = true
        })
        h.run("capture.record(tostring(t.flag))")
        assertEquals(listOf("true"), h.output)
    }

    @Test
    fun `reads property with a custom getter`() {
        h.register(object : TwineNative("t") {
            @TwineProperty val computed: String get() = "a" + "b"
        })
        h.run("capture.record(t.computed)")
        assertEquals(listOf("ab"), h.output)
    }

    @Test
    fun `property has the matching Luau type`() {
        h.register(object : TwineNative("t") {
            @TwineProperty val s = "x"
            @TwineProperty val n = 1.0
            @TwineProperty val b = false
        })
        h.run("""
            capture.record(type(t.s))
            capture.record(type(t.n))
            capture.record(type(t.b))
        """)
        assertEquals(listOf("string", "number", "boolean"), h.output)
    }

    @Test
    fun `reads multiple properties independently`() {
        h.register(object : TwineNative("t") {
            @TwineProperty val first = "one"
            @TwineProperty val second = "two"
            @TwineProperty val third = "three"
        })
        h.run("""
            capture.record(t.first)
            capture.record(t.second)
            capture.record(t.third)
        """)
        assertEquals(listOf("one", "two", "three"), h.output)
    }

    // -- naming --

    @Test
    fun `custom name is used`() {
        h.register(object : TwineNative("t") {
            @TwineProperty("myCustomName") val internalName: String = "custom"
        })
        h.run("capture.record(t.myCustomName)")
        assertEquals(listOf("custom"), h.output)
    }

    @Test
    fun `original name is not accessible when a custom name is set`() {
        h.register(object : TwineNative("t") {
            @TwineProperty("myCustomName") val internalName: String = "custom"
        })
        h.run("capture.record(tostring(t.internalName))")
        assertEquals(listOf("nil"), h.output)
    }

    // -- visibility --

    @Test
    fun `unknown property is nil`() {
        h.register(object : TwineNative("t") {
            @TwineProperty val name = "hello"
        })
        h.run("capture.record(tostring(t.doesNotExist))")
        assertEquals(listOf("nil"), h.output)
    }

    @Test
    fun `unannotated property is not accessible`() {
        h.register(object : TwineNative("t") {
            @Suppress("unused") val hidden = "secret"
            @TwineProperty val visible = "public"
        })
        h.run("""
            capture.record(tostring(t.hidden))
            capture.record(t.visible)
        """)
        assertEquals(listOf("nil", "public"), h.output)
    }

    // -- dynamic values --

    @Test
    fun `reads the value at access time rather than registration time`() {
        val native = object : TwineNative("t") {
            @TwineProperty var count: Int = 0
        }
        h.register(native)
        native.count = 99
        h.run("capture.record(tostring(t.count))")
        assertEquals(listOf("99"), h.output)
    }

    @Test
    fun `sees a change made by a function during the same script`() {
        h.register(object : TwineNative("t") {
            @TwineProperty var count: Int = 0
            @TwineFunction fun bump() { count++ }
        })
        h.run("""
            capture.record(tostring(t.count))
            t.bump()
            t.bump()
            capture.record(tostring(t.count))
        """)
        assertEquals(listOf("0", "2"), h.output)
    }

    // -- writing --

    @Test
    fun `writes a var property from Luau`() {
        val native = object : TwineNative("t") {
            @TwineProperty var title: String = "old"
        }
        h.register(native)
        h.run("t.title = 'new'")
        assertEquals("new", native.title)
    }

    @Test
    fun `written value can be read back in the same script`() {
        h.register(object : TwineNative("t") {
            @TwineProperty var title: String = "old"
        })
        h.run("""
            t.title = "fresh"
            capture.record(t.title)
        """)
        assertEquals(listOf("fresh"), h.output)
    }

    @Test
    fun `writes a numeric var property`() {
        val native = object : TwineNative("t") {
            @TwineProperty var level: Int = 1
        }
        h.register(native)
        h.run("t.level = 10")
        assertEquals(10, native.level)
    }

    @Test
    fun `writing a val property fails`() {
        val native = object : TwineNative("t") {
            @TwineProperty val fixed: String = "const"
        }
        h.register(native)
        h.failure("t.fixed = 'changed'")
        assertEquals("const", native.fixed)
    }

    @Test
    fun `writing a property with the wrong type fails and leaves the value alone`() {
        val native = object : TwineNative("t") {
            @TwineProperty var flag: Boolean = false
        }
        h.register(native)
        h.failure("t.flag = 'no'")
        assertEquals(false, native.flag)
    }

    // -- coexistence --

    @Test
    fun `properties and functions work on the same native`() {
        h.register(object : TwineNative("t") {
            @TwineProperty val prefix: String = "hello"
            @TwineFunction fun greet(name: String) = "$prefix $name"
        })
        h.run("""
            capture.record(t.prefix)
            capture.record(t.greet("world"))
        """)
        assertEquals(listOf("hello", "hello world"), h.output)
    }

    @Test
    fun `property on a native that has overloaded functions`() {
        class Both : TwineNative("both") {
            @TwineProperty val test = "hi"
            @TwineFunction fun hello(test: String) = "s"
            @TwineFunction fun hello(test: Int) = "i"
        }
        h.register(Both())
        h.run("""
            capture.record(both.test)
            capture.record(both.hello("x"))
            capture.record(both.hello(1))
        """)
        assertEquals(listOf("hi", "s", "i"), h.output)
    }

    // -- natives as property values --

    @Test
    fun `reads a native property and calls a function on it`() {
        val inner = object : TwineNative("inner") {
            @TwineFunction fun value() = "from inner"
        }
        h.register(inner)
        h.register(object : TwineNative("outer") {
            @TwineProperty val child: TwineNative = inner
        })
        h.run("capture.record(outer.child.value())")
        assertEquals(listOf("from inner"), h.output)
    }

    @Test
    fun `reads a native property and calls a function on it with colon syntax`() {
        val inner = object : TwineNative("inner") {
            @TwineFunction fun value() = "from inner"
        }
        h.register(inner)
        h.register(object : TwineNative("outer") {
            @TwineProperty val child: TwineNative = inner
        })
        h.run("capture.record(outer.child:value())")
        assertEquals(listOf("from inner"), h.output)
    }
}