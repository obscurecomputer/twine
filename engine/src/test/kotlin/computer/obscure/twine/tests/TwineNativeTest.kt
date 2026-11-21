package computer.obscure.twine.tests

import computer.obscure.twine.TwineHarness
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwineNativeTest {
    private lateinit var h: TwineHarness

    @BeforeEach
    fun setup() {
        h = TwineHarness()
    }

    @Test
    fun `passes a registered native as a parameter`() {
        var received: TwineNative? = null
        val inner = object : TwineNative("inner") {}
        h.register(inner)
        h.register(object : TwineNative("outer") {
            @TwineFunction fun use(n: TwineNative) { received = n }
        })
        h.run("outer.use(inner)")
        assertEquals(inner, received)
    }

    @Test
    fun `returns a native and can call functions on it`() {
        h.register(object : TwineNative("factory") {
            @TwineFunction fun create(): TwineNative = object : TwineNative("product") {
                @TwineFunction fun name() = "created"
            }
        })
        h.run("""
            local p = factory.create()
            capture.record(p.name())
        """)
        assertEquals(listOf("created"), h.output)
    }

    @Test
    fun `returned native can be called with colon syntax`() {
        h.register(object : TwineNative("factory") {
            @TwineFunction fun create(): TwineNative = object : TwineNative("product") {
                @TwineFunction fun name() = "created"
            }
        })
        h.run("""
            local p = factory.create()
            capture.record(p:name())
        """)
        assertEquals(listOf("created"), h.output)
    }

    @Test
    fun `returned native exposes its properties`() {
        h.register(object : TwineNative("factory") {
            @TwineFunction fun create(): TwineNative = object : TwineNative("product") {
                @TwineProperty val label = "gadget"
            }
        })
        h.run("""
            local p = factory.create()
            capture.record(p.label)
        """)
        assertEquals(listOf("gadget"), h.output)
    }

    @Test
    fun `each returned native is its own instance`() {
        class Box(val id: String) : TwineNative("box") {
            @TwineFunction fun id() = id
        }
        h.register(object : TwineNative("factory") {
            @TwineFunction fun make(id: String): TwineNative = Box(id)
        })
        h.run("""
            local a = factory.make("a")
            local b = factory.make("b")
            capture.record(a:id())
            capture.record(b:id())
        """)
        assertEquals(listOf("a", "b"), h.output)
    }

    @Test
    fun `a native handed back to Kotlin is the same object`() {
        val made = object : TwineNative("made") {}
        var seen: TwineNative? = null
        h.register(object : TwineNative("t") {
            @TwineFunction fun make(): TwineNative = made
            @TwineFunction fun take(n: TwineNative) { seen = n }
        })
        h.run("t.take(t.make())")
        assertTrue(seen === made)
    }

    @Test
    fun `natives can be chained through properties`() {
        val leaf = object : TwineNative("leaf") {
            @TwineFunction fun value() = "leaf value"
        }
        val branch = object : TwineNative("branch") {
            @TwineProperty val leaf: TwineNative = leaf
        }
        h.register(object : TwineNative("root") {
            @TwineProperty val branch: TwineNative = branch
        })
        h.run("capture.record(root.branch.leaf.value())")
        assertEquals(listOf("leaf value"), h.output)
    }

    @Test
    fun `natives can be chained through return values`() {
        h.register(object : TwineNative("root") {
            @TwineFunction fun next(): TwineNative = object : TwineNative("mid") {
                @TwineFunction fun next(): TwineNative = object : TwineNative("end") {
                    @TwineFunction fun value() = "deep"
                }
            }
        })
        h.run("capture.record(root.next().next().value())")
        assertEquals(listOf("deep"), h.output)
    }

    @Test
    fun `a native can call back into another registered native`() {
        h.register(object : TwineNative("a") {
            @TwineFunction fun ping() = "pong"
        })
        h.register(object : TwineNative("b") {
            @TwineFunction fun relay() = "relay"
        })
        h.run("""
            capture.record(a.ping())
            capture.record(b.relay())
        """)
        assertEquals(listOf("pong", "relay"), h.output)
    }
}