// !OPT_IN: kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
// LAMBDAS: INDY
// TARGET_BACKEND: JVM_IR

// WITH_STDLIB
// WITH_REFLECT

import kotlin.jvm.JvmSerializableLambda

import kotlin.reflect.KParameter
import kotlin.reflect.jvm.reflect
import kotlin.test.assertEquals
import kotlin.test.assertNull

fun lambda() {
    val f = @JvmSerializableLambda { x: Int, y: String? -> }

    val g = f.reflect()!!

    // TODO: maybe change this name
    assertEquals("<anonymous>", g.name)
    assertEquals(listOf("x", "y"), g.parameters.map { it.name })
    assertEquals(listOf(false, true), g.parameters.map { it.type.isMarkedNullable })
}

fun funExpr() {
    val f = @JvmSerializableLambda fun(x: Int, y: String?) {}

    val g = f.reflect()!!

    // TODO: maybe change this name
    assertEquals("<no name provided>", g.name)
    assertEquals(listOf("x", "y"), g.parameters.map { it.name })
    assertEquals(listOf(false, true), g.parameters.map { it.type.isMarkedNullable })
}

fun extensionFunExpr() {
    val f = @JvmSerializableLambda fun String.(): String = this

    val g = f.reflect()!!

    assertEquals(KParameter.Kind.EXTENSION_RECEIVER, g.parameters.single().kind)
    assertEquals(null, g.parameters.single().name)
}

fun box(): String {
    lambda()
    funExpr()
    extensionFunExpr()

    return "OK"
}
