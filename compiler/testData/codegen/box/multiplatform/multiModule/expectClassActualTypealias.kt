// LANGUAGE: +MultiPlatformProjects
// TARGET_BACKEND: JVM_IR
// MODULE: m1-common
// FILE: common.kt
expect class A {
    fun foo(): String
}

fun test(a: A): String {
    return a.foo()
}

// MODULE: m1-jvm()()(m1-common)
// FILE: B.java
public class B {
    public String foo() {
        return "OK";
    }
}

// FILE: main.kt
actual typealias A = B

fun box() = test(B())

