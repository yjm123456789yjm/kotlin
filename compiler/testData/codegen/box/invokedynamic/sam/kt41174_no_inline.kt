// TARGET_BACKEND: JVM
// WITH_RUNTIME
// FULL_JDK
// JVM_TARGET: 1.8

fun foo(init: (String) -> String): String =
    listOf("OK").stream().map { init(it) }.findFirst().get()

fun bar(init: (String) -> String): String =
    foo { foo(init) }

fun box(): String = bar { it }
