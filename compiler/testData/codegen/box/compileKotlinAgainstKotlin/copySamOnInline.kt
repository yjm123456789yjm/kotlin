// IGNORE_BACKEND_FIR: JVM_IR
// FULL_JDK
// WITH_STDLIB
// SAM_CONVERSIONS: CLASS

// MODULE: lib
// FILE: A.kt

package test

import java.util.concurrent.Callable

class A(val callable: Callable<String>)

inline fun doWork(noinline job: () -> String): Callable<String> {
    val a = A(Callable(job))
    return a.callable
}

var sameModule = doWork { "O" }


// MODULE: main(lib)
// FILE: B.kt

import test.*

fun box(): String {
    val anotherModule = doWork { "K" }

    val sameModuleClassName = sameModule.javaClass.name
    val anotherModuleClassName = anotherModule.javaClass.name
    if (sameModuleClassName == anotherModuleClassName)
        return "Class should be regenerated, but $sameModuleClassName != $anotherModuleClassName"
    if (sameModuleClassName.contains("inlined"))
        return "SAM in same module shouldn't be copied; found $sameModuleClassName"
    if (!anotherModuleClassName.contains("inlined"))
        return "SAM in another module should be copied; found $sameModuleClassName"

    return sameModule.call() + anotherModule.call()
}
