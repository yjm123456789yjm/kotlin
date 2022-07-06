// !OPT_IN: kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
// LAMBDAS: INDY
// TARGET_BACKEND: JVM_IR

// WITH_STDLIB
// WITH_REFLECT

import kotlin.jvm.JvmSerializableLambda

import kotlin.reflect.jvm.reflect

inline class C(val x: Int)

fun C.f(x: (String) -> Unit = @JvmSerializableLambda { OK: String -> }) = x.reflect()?.parameters?.singleOrNull()?.name

fun box(): String = C(0).f() ?: "null"
