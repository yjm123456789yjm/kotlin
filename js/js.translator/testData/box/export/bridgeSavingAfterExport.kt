// MODULE: main
// FILE: main.kt

@JsExport
open class A<T> {
    open fun foo(t: T): T {
        return t
    }
}

class B : A<Int>() {
    override fun foo(t: Int): Int {
        return t
    }
}

fun box(): String {
    val a: A<Int> = B()
    val b: B = B()
    val value: dynamic = "2"

    b.foo(value)

    try {
        a.foo(value)
    } catch (err: ClassCastException) {
       return "OK"
    }

    return "Fail: bridge for B was not generated"
}