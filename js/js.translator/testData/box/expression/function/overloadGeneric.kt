class L<T>

class A {
    fun foo(a: L<Int>) = "Int"
    fun foo(a: L<String>) = "String"
}

fun foo(a: L<Int>) = "Int"
fun foo(a: L<String>) = "String"

fun box(): String {
    if (A().foo(L<Int>()) != "Int") return "fail1"
    if (foo(L<Int>()) != "Int") return "fail2"

    return "OK"
}