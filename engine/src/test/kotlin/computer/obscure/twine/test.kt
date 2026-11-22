package computer.obscure.twine

import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import computer.obscure.twine.engine.TwineContext
import computer.obscure.twine.natives.TwineEnvironment


fun main() {
    class Test : TwineNative("testobj") {
        @TwineProperty
        val test = "hi"

        @TwineFunction
        fun hello(test: String) {
            println("hello")
        }

        @TwineFunction
        fun hello(test: Int): String {
            println("hello integer")
            return "RETURN STRING!!"
        }

        @TwineFunction
        fun callback(test: (String) -> Boolean): Boolean {
            return test.invoke("test string")
        }
    }

    val env = TwineEnvironment()

//    env.registry.add(Test())

    val context = TwineContext()
    context.registry.add(Test())
    context.engine.run(
        """
            print(
                testobj.callback(function(str)
                    if str == "test string" then
                        return true
                    end
                  
                    return false
                end)
            )
        """.trimIndent(),
        env
    )
}