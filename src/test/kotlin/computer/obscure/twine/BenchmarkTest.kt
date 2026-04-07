package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import org.junit.jupiter.api.Test

class BenchmarkNative : TwineNative("benchmark") {
    @TwineProperty
    var count: Int = 0

    @TwineFunction
    fun voidCall() {}

    @TwineFunction
    fun add(a: Int, b: Int): Int = a + b

    @TwineFunction
    fun passTable(data: Map<String, Any>): Int = data.size
}

class BenchmarkTest {
    private fun runScript(script: String): Any? {
        val engine = TwineEngine()
        engine.register(BenchmarkNative())
        return engine.run(script)
    }

    @Test
    fun `benchmark`() {
        runScript("""
            local ITERATIONS = 1000000
            local SHORT_ITERATIONS = 1000000
            
            local function run(name, count, fn)
                for _ = 1, 10000 do fn(1) end
                local start = os.clock()
                for i = 1, count do
                    fn(i)
                end
                local stop = os.clock()
                local total = stop - start
                local avg = (total / count) * 1000000
                print(string.format("[%-20s] Total: %7.4fs | Avg: %7.4f us", name, total, avg))
            end
            
            print("Starting Twine Engine Full Benchmark")
            print("--------------------------------------------------")
            
            run("Pure Luau Math", ITERATIONS, function(i)
                local _ = i * 2 + 10
            end)
            
            run("Kotlin Void Call", ITERATIONS, function()
                benchmark.voidCall()
            end)
            
            run("Kotlin Property Get", ITERATIONS, function()
                local _ = benchmark.count
            end)
            
            run("Kotlin Property Set", ITERATIONS, function(i)
                benchmark.count = i
            end)
            
            run("Kotlin Math (i+i)", ITERATIONS, function(i)
                benchmark.add(i, i)
            end)
            
            run("Table Marshal (3pc)", SHORT_ITERATIONS, function()
                benchmark.passTable({
                    name = "FIRE",
                    id = 123,
                    active = true
                })
            end)
            
            print("--------------------------------------------------")
            print("Benchmark Complete")
        """.trimIndent())
    }

}