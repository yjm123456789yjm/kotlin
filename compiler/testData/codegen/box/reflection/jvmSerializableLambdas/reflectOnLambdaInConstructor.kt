// !OPT_IN: kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
// LAMBDAS: INDY
// TARGET_BACKEND: JVM_IR

// WITH_STDLIB
// WITH_REFLECT

import kotlin.jvm.JvmSerializableLambda
import kotlin.reflect.jvm.reflect

class C {
    val o = @JvmSerializableLambda { O: String -> }
    val k = @JvmSerializableLambda { K: String -> }

    constructor(y: Int)
    constructor(y: String)
}

fun box(): String =
    (C(0).o.reflect()?.parameters?.singleOrNull()?.name ?: "null") +
            (C("").k.reflect()?.parameters?.singleOrNull()?.name ?: "null")
