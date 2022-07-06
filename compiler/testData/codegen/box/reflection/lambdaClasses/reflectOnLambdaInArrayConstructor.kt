// !OPT_IN: kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
// TARGET_BACKEND: JVM
// WITH_REFLECT
// LAMBDAS: CLASS

import kotlin.reflect.jvm.*
import kotlin.test.assertNotNull

fun box(): String {
    assertNotNull({}.reflect())
    assertNotNull(Array(1) { {} }.single().reflect())

    return "OK"
}
