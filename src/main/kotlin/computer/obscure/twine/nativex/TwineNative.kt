/**
 * Copyright 2025 znci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package computer.obscure.twine.nativex

import computer.obscure.twine.TwineTable
import computer.obscure.twine.nativex.classes.NativeProperty
import org.luaj.vm2.Globals

/**
 * Abstract class TwineNative serves as a bridge between Kotlin and Lua, allowing functions and properties
 * to be dynamically registered in a Lua table.
 *
 * Code is written as Kotlin, and is converted to Lua if the appropriate function/property has the correct annotation.
 *
 * Functions with the {@code TwineNativeFunction} annotation will be registered, and properties with the {@code TwineNativeProperty} annotation.
 */
@Suppress("unused")
abstract class TwineNative(
    /** The name of the Lua table/property for this object. */
    override var valueName: String = ""
) : TwineTable(valueName) {
    private var finalized = false
    private var properties: Map<String, NativeProperty> = mutableMapOf()

    companion object {
        const val INHERIT_TAG = "INHERIT_FROM_DEFINITION"
    }

    /**
     * Initializes the TwineNative instance by registering its functions and properties.
     */
    init {
        val functionRegistrar = FunctionRegistrar(this)
        functionRegistrar.register()
    }

    /**
     * Runs immediately after the native is registered in a [TwineEngine].
     */
    internal fun __finalizeNative() {
        if (finalized) return
        finalized = true

        val propertyRegistrar = PropertyRegistrar(this)
        properties = PropertyRegistrar.getProperties(this)
        propertyRegistrar.registerProperties(properties)
    }

    protected fun requireFinalized() {
        check(finalized) {
            "TwineNative '$valueName' used before being registered with TwineEngine!"
        }
    }

    fun toCodeBlock(globals: Globals): String {
        requireFinalized()

        val props = properties
        val sb = StringBuilder()

        for ((name, prop) in props) {
            val currentValue = prop.getter.call(this)
            val defaultValue = prop.defaultValue

            println("${prop.name}: $defaultValue -> $currentValue")

            if (currentValue != defaultValue) {
                sb.append("$valueName.$name = ${serializeLua(currentValue, globals)}\n")
            }
        }

        return sb.toString().trimEnd()
    }

    fun serializeLua(value: Any?, globals: Globals): String = when (value) {
        null -> "nil"
        is Boolean, is Int, is Long, is Float, is Double -> value.toString()
        is String -> "\"${value.replace("\"", "\\\"")}\""
        is TwineNative -> value.toCodeBlock(globals)
        else -> error("Unsupported Lua value: ${value::class}")
    } as String

}
