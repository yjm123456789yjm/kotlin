// !OPT_IN: kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
// LAMBDAS: INDY
// TARGET_BACKEND: JVM_IR
// WITH_STDLIB
// WITH_REFLECT
// WITH_COROUTINES
// FILE: a.kt

import kotlin.jvm.JvmSerializableLambda
import helpers.*
import kotlin.coroutines.*
import kotlin.reflect.jvm.reflect

suspend fun f() = @JvmSerializableLambda { OK: String -> }

fun box(): String {
    lateinit var x: (String) -> Unit
    suspend {
        x = f()
    }.startCoroutine(EmptyContinuation)
    return x.reflect()?.parameters?.singleOrNull()?.name ?: "null"
}
