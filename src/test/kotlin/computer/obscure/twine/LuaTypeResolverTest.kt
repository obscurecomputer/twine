package computer.obscure.twine

import net.hollowcube.luau.LuaState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuaTypeResolverTest {
    private lateinit var state: LuaState

    @BeforeEach
    fun setup() {
        state = LuaState.newState()
    }

    @AfterEach
    fun teardown() {
        state.close()
    }

    @Test
    fun `matches String when string on stack`() {
        state.pushString("hello")
        assertTrue(LuaTypeResolver.matches(state, 1, String::class, emptyMap()))
    }

    @Test
    fun `does not match String when number on stack`() {
        state.pushNumber(1.0)
        assertFalse(LuaTypeResolver.matches(state, 1, String::class, emptyMap()))
    }

    @Test
    fun `matches Any for anything`() {
        state.pushString("hello")
        assertTrue(LuaTypeResolver.matches(state, 1, Any::class, emptyMap()))
        state.pushNumber(1.0)
        assertTrue(LuaTypeResolver.matches(state, 2, Any::class, emptyMap()))
        state.pushBoolean(true)
        assertTrue(LuaTypeResolver.matches(state, 3, Any::class, emptyMap()))
    }

    @Test
    fun `read returns correct String`() {
        state.pushString("hello")
        assertEquals("hello", LuaTypeResolver.read(state, 1, String::class, emptyMap()))
    }

    @Test
    fun `read returns correct Int`() {
        state.pushInteger(42)
        assertEquals(42, LuaTypeResolver.read(state, 1, Int::class, emptyMap()))
    }

    @Test
    fun `read returns correct Double`() {
        state.pushNumber(3.14)
        assertEquals(3.14, LuaTypeResolver.read(state, 1, Double::class, emptyMap()))
    }

    @Test
    fun `read resolves TwineNative from table`() {
        val native = object : TwineNative("myNative") {}
        val natives = mapOf("myNative" to native)

        state.newTable()
        state.pushString("myNative")
        state.setField(-2, "__twineName")

        val result = LuaTypeResolver.read(state, 1, TwineNative::class, natives)
        assertEquals(native, result)
    }

    @Test
    fun `read throws when native not registered`() {
        state.newTable()
        state.pushString("unknown")
        state.setField(-2, "__twineName")

        assertThrows<IllegalStateException> {
            LuaTypeResolver.read(state, 1, TwineNative::class, emptyMap())
        }
    }
}