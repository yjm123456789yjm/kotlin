// LANGUAGE: +GenericInlineClassParameter
// IGNORE_BACKEND: JVM
// TARGET_BACKEND: JVM
// WITH_STDLIB

@JvmInline
value class AAAA<T : Any>(val x: List<T>) {
    fun <R: Any> equalsChecks2(x: AAAA<R>) {}
}

class A {
    fun equalsChecks1(x: AAAA<List<Int>>) {}
}

fun box(): String {
    var paramTypes = A::class.java.methods.find { it.name.contains("equalsChecks1") }!!.genericParameterTypes.toList().toString()
    if (paramTypes != "[java.util.List<? extends java.util.List<java.lang.Integer>>]") return "FAIL 1: $paramTypes"
    paramTypes = AAAA::class.java.methods.find { it.name.contains("equalsChecks2") }!!.genericParameterTypes.toList().toString()
    if (paramTypes != "[java.util.List<? extends T>, java.util.List<? extends R>]") return "FAIL 2: $paramTypes"
    return "OK"
}
