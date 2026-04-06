package computer.obscure.twine

object LuaRegistry {
    const val REGISTRY = -10000
    const val ENVIRON  = -10001
    const val GLOBALS  = -10002
    
    /**
     * Calculates the upvalue index for a C function.
     */
    fun upvalue(i: Int): Int = GLOBALS - i
}