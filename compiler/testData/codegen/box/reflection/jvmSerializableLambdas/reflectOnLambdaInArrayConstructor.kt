// !OPT_IN: kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
// LAMBDAS: INDY
// TARGET_BACKEND: JVM_IR

// WITH_STDLIB
// WITH_REFLECT

import kotlin.jvm.JvmSerializableLambda

import kotlin.reflect.jvm.*
import kotlin.test.assertNotNull

fun box(): String {
    assertNull({}.reflect())
    assertNotNull((@JvmSerializableLambda {}).reflect())
    assertNotNull(Array(1) {@JvmSerializableLambda {} }.single().reflect())

    return "OK"
}
