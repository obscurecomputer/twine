package computer.obscure.twine.natives

import computer.obscure.twine.TwineNative
import computer.obscure.twine.annotations.TwineFunction
import computer.obscure.twine.annotations.TwineProperty
import computer.obscure.twine.exceptions.TwineException
import computer.obscure.twine.exceptions.TwineExecutionException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaSetter

class ParamSpec(
    val name: String?,
    val type: KType,
    val klass: Class<*>?,
    val nullable: Boolean,
    val optional: Boolean,
    val isVararg: Boolean,
    val elementType: KType?,
    val elementClass: Class<*>?,
)

class BoundFunction(val name: String, val function: KFunction<*>) {
    val params: Array<ParamSpec> = function.parameters.drop(1).map { p ->
        val t = p.type
        val el = if (p.isVararg) t.arguments.firstOrNull()?.type else null

        ParamSpec(
            name = p.name,
            type = t,
            klass = (t.classifier as? KClass<*>)?.javaObjectType,
            nullable = t.isMarkedNullable,
            optional = p.isOptional,
            isVararg = p.isVararg,
            elementType = el,
            elementClass = (el?.classifier as? KClass<*>)?.javaObjectType
        )
    }.toTypedArray()

    val isVararg = params.lastOrNull()?.isVararg == true
    val fixedCount = if (isVararg) params.size - 1 else params.size
    val required = params.count { !it.isVararg && !it.optional }

    val returnClass: Class<*>? =
        (function.returnType.classifier as? KClass<*>)?.javaObjectType

    private val returnsUnit = returnClass == Unit::class.javaObjectType
    private val method: Method? = function.javaMethod?.takeIf { it.trySetAccessible() }

    fun accepts(argCount: Int) =
        argCount >= required && (isVararg || argCount <= fixedCount)

    fun typeAt(i: Int): KType? =
        if (i < fixedCount) params[i].type else params.lastOrNull()?.elementType

    fun call(target: Any, args: Array<Any?>): Any? {
        val m = method ?: return try {
            function.call(target, *args)
        } catch (e: Throwable) {
            throw TwineExecutionException(name, e)
        }

        return try {
            m.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw TwineExecutionException(name, e.targetException)
        } catch (e: IllegalArgumentException) {
            val details = describeArgumentErrors(args)

            throw TwineException(
                "Failed to invoke $name:\n$details",
                e
            )
        } catch (e: ReflectiveOperationException) {
            throw TwineException(
                "Could not invoke $name: ${e.message}",
                e
            )
        }
    }

    fun getSignature(): String {
        val parameters = params.joinToString(", ") { param ->
            val name = param.name ?: "arg"
            val type = param.type.toString()
            when {
                param.isVararg -> "vararg $name: ${param.elementType}"
                param.optional -> "$name: $type = ..."
                else -> "$name: $type"
            }
        }

        return "($parameters): ${function.returnType}"
    }

    private fun describeArgumentErrors(args: Array<Any?>): String {
        return params.mapIndexedNotNull { i, param ->
            val value = args.getOrNull(i)
            val expected = param.type.toString()
            val actual = value?.javaClass?.simpleName ?: "nil"

            val valid = when {
                value == null -> param.nullable || param.optional
                param.klass == null -> true
                else -> param.klass.isInstance(value)
            }

            if (valid) null
            else buildString {
                append("  #${i + 1} '${param.name ?: "arg"}': ")
                append("expected $expected, got $actual")
            }
        }.joinToString("\n")
    }
}

class BoundProperty(val name: String, val property: KProperty1<Any, *>) {
    val klass: Class<*>? =
        (property.returnType.classifier as? KClass<*>)?.javaObjectType

    val mutable = property is KMutableProperty1<*, *>
    val nullable = property.returnType.isMarkedNullable

    private val getter: Method? = property.javaGetter?.takeIf { it.trySetAccessible() }
    private val setter: Method? =
        (property as? KMutableProperty1<*, *>)?.javaSetter?.takeIf { it.trySetAccessible() }

    fun get(target: Any): Any? =
        if (getter != null) getter.invoke(target) else property.get(target)

    @Suppress("UNCHECKED_CAST")
    fun set(target: Any, value: Any?) {
        if (setter != null) setter.invoke(target, value)
        else (property as KMutableProperty1<Any, Any?>).set(target, value)
    }
}

class ClassModel private constructor(klass: KClass<*>) {
    val functionList: List<BoundFunction> = klass.functions.mapNotNull { f ->
        f.findAnnotation<TwineFunction>()?.let { a ->
            BoundFunction(a.name.takeIf { it != TwineNative.INHERIT_TAG } ?: f.name, f)
        }
    }

    @Suppress("UNCHECKED_CAST")
    val propertyList: List<BoundProperty> = klass.memberProperties.mapNotNull { p ->
        p.findAnnotation<TwineProperty>()?.let { a ->
            BoundProperty(
                a.name.takeIf { it != TwineNative.INHERIT_TAG } ?: p.name,
                p as KProperty1<Any, *>,
            )
        }
    }

    val functions = functionList.groupBy { it.name }
    val properties = propertyList.associateBy { it.name }

    companion object {
        private val cache = object : ClassValue<ClassModel>() {
            override fun computeValue(type: Class<*>) = ClassModel(type.kotlin)
        }
        fun of(native: TwineNative): ClassModel = cache.get(native.javaClass)
    }
}