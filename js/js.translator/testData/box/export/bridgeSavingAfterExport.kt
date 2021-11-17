// MODULE: main
// FILE: main.kt

@JsExport
open class A {
    open fun foo(): Any? = "O"
}

@JsExport
class B: A() {
    override fun foo(): Any = "K"
}

fun box(): String {
    val a = A()
    val b = B()
    val o = a.foo()?.toString() ?: ""
    val k = b.foo()?.toString() ?: ""
    return o + k
}