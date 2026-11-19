package computer.obscure.twine.exceptions

import computer.obscure.twine.natives.BoundFunction

class TwineOverloadException(
    functionName: String,
    args: List<String>,
    options: List<BoundFunction>
) : TwineException(
    buildString {
        appendLine("No matching overload for $functionName(${args.joinToString(", ")})")
        appendLine("Available overloads:")

        options.forEach {
            appendLine("  $functionName${it.getSignature()}")
        }
    }.trimEnd()
)