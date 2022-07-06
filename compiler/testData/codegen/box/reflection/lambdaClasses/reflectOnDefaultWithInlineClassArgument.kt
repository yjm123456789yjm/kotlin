// !OPT_IN: kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
// TARGET_BACKEND: JVM
// WITH_REFLECT
// LAMBDAS: CLASS

import kotlin.reflect.jvm.reflect

inline class C(val x: Int)

fun C.f(x: (String) -> Unit = { OK: String -> }) = x.reflect()?.parameters?.singleOrNull()?.name

fun box(): String = C(0).f() ?: "null"
