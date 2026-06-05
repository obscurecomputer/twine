package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import net.hollowcube.luau.LuaGcOp
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TestVec2 : TwineNative("vec2") {
    @TwineFunction
    fun of(x: Double, y: Double) = TestVec2()
}

class TwineLeakTest {
    @Test
    fun `test proxy native GC`() {
        val engine = TwineEngine()

        engine.register(TestVec2())

        val script = """
            for i = 1, 10000 do
                local v = vec2.of(i, i)
            end
        """

        engine.run(script)
        System.gc()
        engine.state.gc(LuaGcOp.COLLECT, 0)
        engine.state.gc(LuaGcOp.COLLECT, 0)

        val liveProxies = engine.proxyNatives.values.count {
            it.get() != null
        }
        assertTrue(liveProxies == 0 || liveProxies < 10000, "Native objects were not collected")

        engine.close()
    }
}