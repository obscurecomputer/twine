package computer.obscure.twine.tests

import computer.obscure.twine.BenchBig
import computer.obscure.twine.BenchEmpty
import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.engine.TwineContext
import computer.obscure.twine.natives.TwineEnvironment
import net.hollowcube.luau.LuaGcOp
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object GcResults {
    val lines = mutableListOf<Pair<String, Double>>()
    val values = mutableMapOf<String, Double>()
}

private const val RESET = "\u001B[0m"

enum class Status(val label: String, val color: String) {
    PASS("PASS", "\u001B[32m"),
    WARN("WARN", "\u001B[33m"),
    FAIL("FAIL", "\u001B[31m"),
}

class GcCheck(val name: String, val status: Status, val detail: String)

class GcHarness(
    private val context: TwineContext,
    private val env: TwineEnvironment,
) : TwineNative("gc") {

    @TwineFunction
    fun collect(): Double {
        val engine = context.engine
        synchronized(engine.lock) {
            val t0 = System.nanoTime()
            engine.state.gc(LuaGcOp.COLLECT, 0)
            return (System.nanoTime() - t0) / 1_000_000.0
        }
    }

    @TwineFunction
    fun runtimeCount(): Int = env.runtimeRegistry.allKeys().size

    @TwineFunction
    fun luaKb(): Double = context.engine.state.gc(LuaGcOp.COUNT, 0).toDouble()

    @TwineFunction
    fun record(name: String, value: Double) {
        GcResults.lines += name to value
        GcResults.values[name] = value
    }
}

class GcFactory : TwineNative("fx") {
    private val sharedInstance = BenchBig()

    @TwineFunction
    fun makeEmpty(): BenchEmpty = BenchEmpty()

    @TwineFunction
    fun shared(): BenchBig = sharedInstance
}

class TwineGcBenchmarkTest {
    private lateinit var context: TwineContext
    private lateinit var env: TwineEnvironment

    companion object {
        private const val ALLOCS = 200_000
        private const val SUSTAINED = 500_000
        private const val LIVE = 100_000

        private val useColor = System.getenv("NO_COLOR") == null

        private fun paint(text: String, status: Status) =
            if (useColor) "${status.color}$text$RESET" else text

        private fun buildChecks(): List<GcCheck> {
            val checks = mutableListOf<GcCheck>()

            fun exact(name: String, key: String, expected: Double) {
                val actual = GcResults.values[key]
                checks += when {
                    actual == null ->
                        GcCheck(name, Status.FAIL, "not recorded (did the test fail earlier?)")
                    actual == expected ->
                        GcCheck(name, Status.PASS, "${actual.toLong()}")
                    else ->
                        GcCheck(name, Status.FAIL, "expected ${expected.toLong()}, got ${actual.toLong()}")
                }
            }

            fun remaining(name: String, key: String) {
                val left = GcResults.values[key]
                checks += when {
                    left == null ->
                        GcCheck(name, Status.FAIL, "not recorded (did the test fail earlier?)")
                    left == 0.0 ->
                        GcCheck(name, Status.PASS, "0 left")
                    left == 1.0 ->
                        GcCheck(name, Status.WARN, "1 left, probably a stale stack slot")
                    else ->
                        GcCheck(name, Status.FAIL, "${left.toLong()} left, looks like a leak")
                }
            }

            remaining("reclaims everything after a full collect", "reclaim: natives after collect")

            exact("shared native stays registered while a proxy is alive", "survive: natives while proxy alive", 1.0)
            exact("surviving proxy is still callable", "survive: call through surviving proxy", 1.0)
            remaining("shared native removed after its last proxy drops", "survive: natives after proxy dropped")

            val peak = GcResults.values["sustained: peak runtime natives"]
            checks += if (peak == null) {
                GcCheck("registry stays bounded under sustained allocation", Status.FAIL, "not recorded")
            } else {
                val pct = peak / SUSTAINED * 100.0
                val detail = "peak ${peak.toLong()} of $SUSTAINED allocations (%.2f%%)".format(pct)
                val status = when {
                    pct < 5.0 -> Status.PASS
                    pct < 25.0 -> Status.WARN
                    else -> Status.FAIL
                }
                GcCheck("registry stays bounded under sustained allocation", status, detail)
            }
            remaining("registry empty after final collect (sustained)", "sustained: natives after final collect")

            exact("held natives are not reclaimed while referenced", "live: natives after collect, all alive", LIVE.toDouble())
            remaining("all natives reclaimed once released", "live: natives after release")

            val released = GcResults.values["live: full collect, all released (ms)"]
            checks += if (released == null) {
                GcCheck("collect cost per released native", Status.FAIL, "not recorded")
            } else {
                val us = released * 1000.0 / LIVE
                val status = if (us < 0.5) Status.PASS else Status.WARN
                GcCheck("collect cost per released native", status, "%.2f us (warns at 0.50 us or more)".format(us))
            }

            return checks
        }

        @JvmStatic
        @AfterAll
        fun report() {
            println()
            println("gc benchmark")
            GcResults.lines.forEach { (name, value) ->
                println("%-62s %14.2f".format(name, value))
            }
            println()

            val checks = buildChecks()
            val failed = checks.count { it.status == Status.FAIL }
            val warned = checks.count { it.status == Status.WARN }

            println("summary")
            checks.forEach { c ->
                println("  ${paint(c.status.label, c.status)}  ${c.name.padEnd(54)} ${c.detail}")
            }
            println()

            val overall = when {
                failed > 0 -> Status.FAIL
                warned > 0 -> Status.WARN
                else -> Status.PASS
            }
            val verdict = when (overall) {
                Status.FAIL -> "FAIL: $failed of ${checks.size} checks failed"
                Status.WARN -> "PASS WITH WARNINGS: $warned of ${checks.size} checks need a look"
                Status.PASS -> "PASS: all ${checks.size} checks passed"
            }
            println(paint(verdict, overall))
            println()

            assertTrue(GcResults.lines.isNotEmpty(), "no gc benchmark recorded anything")
            assertTrue(failed == 0, verdict)
        }
    }

    @BeforeEach
    fun setup() {
        context = TwineContext()
        env = TwineEnvironment()
        env.registry.add(GcHarness(context, env))
        env.registry.add(GcFactory())
    }

    private fun run(script: String) = context.engine.run(script.trimIndent(), env)

    private fun value(name: String): Double =
        GcResults.values[name] ?: error("nothing recorded for '$name'")

    // -- reclaim --

    @Test
    fun `a full collect reclaims runtime natives`() {
        run("""
            for _ = 1, $ALLOCS do local o = fx.makeEmpty() end
            gc.record("reclaim: natives before collect", gc.runtimeCount())
            gc.record("reclaim: lua heap before collect (KB)", gc.luaKb())
            gc.record("reclaim: full collect (ms)", gc.collect())
            gc.record("reclaim: natives after collect", gc.runtimeCount())
            gc.record("reclaim: lua heap after collect (KB)", gc.luaKb())
        """)

        assertTrue(
            value("reclaim: natives after collect") <= 1,
            "registry still holds ${value("reclaim: natives after collect")} runtime natives after a full collect"
        )
    }

    // -- survival --

    @Test
    fun `a native stays registered while a proxy is alive and is removed after`() {
        run("""
            local keep = fx.shared()
            for _ = 1, 5000 do local o = fx.shared() end
            gc.collect()
            gc.record("survive: natives while proxy alive", gc.runtimeCount())
            gc.record("survive: call through surviving proxy", keep.f1())

            keep = nil
            gc.collect()
            gc.record("survive: natives after proxy dropped", gc.runtimeCount())
        """)

        assertEquals(1.0, value("survive: natives while proxy alive"))
        assertEquals(1.0, value("survive: call through surviving proxy"))
        assertTrue(value("survive: natives after proxy dropped") <= 1)
    }

    // -- sustained allocation --

    @Test
    fun `registry stays bounded during sustained allocation`() {
        run("""
            local peak = 0
            for i = 1, $SUSTAINED do
                local o = fx.makeEmpty()
                if i % 10000 == 0 then
                    local c = gc.runtimeCount()
                    if c > peak then peak = c end
                end
            end
            gc.record("sustained: allocations", $SUSTAINED)
            gc.record("sustained: peak runtime natives", peak)
            gc.record("sustained: natives after final collect", (function() gc.collect() return gc.runtimeCount() end)())
        """)

        assertTrue(
            value("sustained: peak runtime natives") < SUSTAINED / 4.0,
            "registry grew to ${value("sustained: peak runtime natives")} natives, which looks like a leak"
        )
    }

    // -- cost of collecting --

    @Test
    fun `full collect cost with many live natives`() {
        run("""
            local live = {}
            for i = 1, $LIVE do live[i] = fx.makeEmpty() end
            gc.record("live: natives held", gc.runtimeCount())
            gc.record("live: full collect, all alive (ms)", gc.collect())
            gc.record("live: natives after collect, all alive", gc.runtimeCount())

            live = nil
            gc.record("live: full collect, all released (ms)", gc.collect())
            gc.record("live: natives after release", gc.runtimeCount())
        """)

        assertEquals(LIVE.toDouble(), value("live: natives after collect, all alive"))
        assertTrue(value("live: natives after release") <= 1)
    }
}