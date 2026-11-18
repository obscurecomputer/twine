package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import computer.obscure.twine.engine.TwineContext
import computer.obscure.twine.natives.TwineEnvironment
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class BenchEmpty : TwineNative("bench_empty")

class BenchOneProp : TwineNative("bench_oneprop") {
    @TwineProperty
    val x: Int = 1
}

class BenchBig : TwineNative("bench_big") {
    @TwineFunction fun f1(): Int = 1
    @TwineFunction fun f2(): Int = 2
    @TwineFunction fun f3(): Int = 3
    @TwineFunction fun f4(): Int = 4
    @TwineFunction fun f5(): Int = 5
    @TwineFunction fun f6(): Int = 6
    @TwineFunction fun f7(): Int = 7
    @TwineFunction fun f8(): Int = 8
    @TwineFunction fun f9(): Int = 9
    @TwineFunction fun f10(): Int = 10
}

class BenchVec(
    @TwineProperty var x: Int,
    @TwineProperty var y: Int,
    @TwineProperty var z: Int,
) : TwineNative("bench_vec") {
    @TwineFunction
    fun add(x: Int, y: Int, z: Int): BenchVec =
        BenchVec(this.x + x, this.y + y, this.z + z)
}

object BenchResults {
    val rows = mutableListOf<Pair<String, Double>>()
}

class BenchHarness : TwineNative("bench") {
    @TwineFunction
    fun now(): Double = System.nanoTime() / 1_000_000_000.0

    @TwineFunction
    fun record(name: String, microsPerOp: Double) {
        BenchResults.rows += name to microsPerOp
    }
}

class BenchFactory : TwineNative("fx") {
    @TwineFunction fun noop(): Int = 0
    @TwineFunction fun sum3(a: Int, b: Int, c: Int): Int = a + b + c
    @TwineFunction fun echo(s: String): String = s

    @TwineFunction fun makeEmpty(): BenchEmpty = BenchEmpty()
    @TwineFunction fun makeOneProp(): BenchOneProp = BenchOneProp()
    @TwineFunction fun makeBig(): BenchBig = BenchBig()
    @TwineFunction fun makeVec(x: Int, y: Int, z: Int): BenchVec = BenchVec(x, y, z)

//    @TwineFunction
//    fun takeCallback(cb: LuaCallback) {
//        cb.release()
//    }
}

class TwineAllocationBenchmarkTest {
    private lateinit var context: TwineContext
    private lateinit var env: TwineEnvironment

    companion object {
        private const val WARMUP = 50000
        private const val N = 200000

        @JvmStatic
        @AfterAll
        fun report() {
            val base = BenchResults.rows.firstOrNull { it.first.startsWith("native call, no args") }?.second
            println()
            println("allocation benchmark ($N iterations, $WARMUP warmup)")
            BenchResults.rows.sortedBy { it.second }.forEach { (name, us) ->
                val ratio = if (base != null && base > 0) "  (%.1fx noop)".format(us / base) else ""
                println("%-52s %10.2f us/op%s".format(name, us, ratio))
            }
            println()
            assertTrue(BenchResults.rows.isNotEmpty(), "no benchmark recorded anything")
        }
    }

    @BeforeEach
    fun setup() {
        context = TwineContext()

        env = TwineEnvironment()
        env.registry.add(BenchHarness())
        env.registry.add(BenchFactory())
    }

    @AfterEach
    fun teardown() {
//        context.close()
    }

    private fun bench(name: String, body: String, setup: String = "", n: Int = N) {
        val script = """
            $setup
            for _ = 1, $WARMUP do
                $body
            end
            local t0 = bench.now()
            for _ = 1, $n do
                $body
            end
            local dt = bench.now() - t0
            bench.record("$name", dt / $n * 1000000)
        """.trimIndent()
        context.engine.run(script, env)
    }

    @Test
    fun `lua table with 3 fields`() =
        bench("lua: table {x,y,z}", "local t = {x = 1, y = 2, z = 3}")

    @Test
    fun `lua function call`() =
        bench(
            "lua: plain function call",
            "local r = f(1, 2, 3)",
            setup = "local function f(a, b, c) return a + b + c end",
        )

    @Test
    fun `native call no args`() =
        bench("native call, no args", "local r = fx.noop()")

    @Test
    fun `native call three ints`() =
        bench("native call, 3 int args", "local r = fx.sum3(1, 2, 3)")

    @Test
    fun `native call string in and out`() =
        bench("native call, string in/out", "local r = fx.echo('hello')")

    @Test
    fun `alloc empty native`() =
        bench("alloc: native with no members", "local o = fx.makeEmpty()")

    @Test
    fun `alloc native with one property`() =
        bench("alloc: native with 1 property", "local o = fx.makeOneProp()")

    @Test
    fun `alloc native with ten functions`() =
        bench("alloc: native with 10 functions", "local o = fx.makeBig()")

    @Test
    fun `alloc vec`() =
        bench("alloc: Vec(x,y,z) via factory", "local o = fx.makeVec(1, 2, 3)")

//    @Test
//    fun `method call returning a new vec`() =
//        bench(
//            "vec.add(1,2,3) -> new Vec",
//            "local r = v.add(1, 2, 3)",
//            setup = "local v = fx.makeVec(1, 2, 3)",
//        )

    @Test
    fun `method lookup without calling`() =
        bench(
            "vec.add lookup only (no call)",
            "local f = v.add",
            setup = "local v = fx.makeVec(1, 2, 3)",
        )

    @Test
    fun `property read`() =
        bench(
            "vec.x read",
            "local r = v.x",
            setup = "local v = fx.makeVec(1, 2, 3)",
        )

    @Test
    fun `property write`() =
        bench(
            "vec.x = 5 write",
            "v.x = 5",
            setup = "local v = fx.makeVec(1, 2, 3)",
        )

//    @Test
//    fun `lua closure passed to kotlin`() =
//        bench("pass Lua closure to Kotlin (capture+release)", "fx.takeCallback(function() end)")

//    @Test
//    fun `kotlin invokes lua callback`() {
//        lateinit var callback: LuaCallback
//
//        engine.register(object : TwineNative("t") {
//            @TwineFunction
//            fun set(cb: LuaCallback) {
//                callback = cb
//            }
//        })
//        engine.run("t.set(function(a, b, c) return a end)")
//
//        repeat(WARMUP) { callback.invoke(1.0, 2.0, 3.0) }
//        val t0 = System.nanoTime()
//        repeat(N) { callback.invoke(1.0, 2.0, 3.0) }
//        val micros = (System.nanoTime() - t0) / 1_000.0 / N
//
//        BenchResults.rows += "kotlin -> Lua callback (3 args)" to micros
//    }
}