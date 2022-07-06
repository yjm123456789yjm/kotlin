// !OPT_IN: kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
// LANGUAGE: +GenerateIndyLambdasOnJvmByDefault
// TARGET_BACKEND: JVM_IR

// WITH_STDLIB
// WITH_REFLECT

import kotlin.jvm.JvmSerializableLambda

import kotlin.reflect.jvm.reflect

val x = @JvmSerializableLambda { OK: String -> }

fun box(): String {
    return x.reflect()?.parameters?.singleOrNull()?.name ?: "null"
}
