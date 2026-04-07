package computer.obscure.twine

data class TwineStats(
    val luaMemoryKb: Int,
    val registeredNativesCount: Int,
    val cachedNativesCount: Int,
    val jvmHeapUsedMb: Long
)