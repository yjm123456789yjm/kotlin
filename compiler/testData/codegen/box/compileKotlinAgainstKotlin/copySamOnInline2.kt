// IGNORE_BACKEND_FIR: JVM_IR
// FULL_JDK
// WITH_STDLIB
// SAM_CONVERSIONS: CLASS

// MODULE: lib
// FILE: A.kt

package test

import java.util.concurrent.Callable

inline fun doWork(noinline job: () -> String): Callable<String> {
    return Callable(job)
}

// MODULE: main(lib)
// FILE: B.kt

import test.*

fun box(): String {
    val anotherModule = doWork { "K" }

    val anotherModuleClassName = anotherModule.javaClass.name
    if (anotherModuleClassName != "BKt\$inlined\$sam\$i\$java_util_concurrent_Callable\$0")
        return "class should be regenerated, but $anotherModuleClassName"

    return "OK"
}
