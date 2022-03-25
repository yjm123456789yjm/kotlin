// KJS_WITH_FULL_RUNTIME

class L<T>

class A {
    fun foo(a: L<Int>) = "Int"
    fun foo(a: L<String>) = "String"
    fun L<Int>.bar() = "Int2"
    fun L<String>.bar() = "String2"
}

fun foo(a: L<Int>) = "Int"
fun foo(a: L<String>) = "String"

private class TestClass {
    private val data = mutableListOf<List<Any>>()
    fun withData(data: List<List<Any>>) = apply { this.data.addAll(data) }
    fun withData(row: List<Any>) = apply {
        data.add(row)
    }

    fun getCols(): Int {
        return data.firstOrNull()?.size ?: return 0
    }
}

fun box(): String {
    if (A().foo(L<Int>()) != "Int") return "fail1"
    A().apply {
        if (L<Int>().bar() != "Int2") return "fail2"
    }
    if (foo(L<Int>()) != "Int") return "fail3"

    val b = TestClass()
    val data = mutableListOf<List<Any>>()
    data.add(listOf("a", "b", "c"))
    data.add(listOf("d", "e", "f"))
    b.withData(data)
    if (b.getCols() != 3) return "fail4"

    return "OK"
}