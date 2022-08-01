// EXPECTED_REACHABLE_NODES: 1281
package foo

class A(val a: Int = 2, val b: Int = 3) {
    fun foo() = a + b
}

fun box(): String {
    if (A(b = 4).foo() != 6) return "fail0"
    if (A(a = 4).foo() != 7) return "fail0"
    if (A(1, 2).foo() != 3) return "fail1"
    if (A(1, 3).foo() != 4) return "fail2"
    if (A(3).foo() != 6) return "fail3"
    if (A().foo() != 5) return "fail4"

    return "OK"
}

