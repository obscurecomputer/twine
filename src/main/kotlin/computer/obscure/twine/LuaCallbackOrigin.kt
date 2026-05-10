package computer.obscure.twine

data class LuaCallbackOrigin(
    val source: String,
    val lineDefined: Int,
    val inferredName: String?,
    val argCount: Int = 0,
    val isVariadic: Boolean = false
) {
    override fun toString() = buildString {
        append(source)
        if (lineDefined > 0) append(":$lineDefined")
        append(" [")
        append(inferredName ?: "anonymous")
        append(" / ")
        append(argCount)
        if (isVariadic) append("+")
        append(" args]")
    }

    companion object {
        val UNKNOWN = LuaCallbackOrigin("?", -1, null)
    }
}