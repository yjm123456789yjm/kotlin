// WITH_STDLIB

@JvmInline
value class Inlined(val value: Int)

sealed interface A {
    val property: Inlined?

    fun foo(): Inlined?
}

class B : A {
    override val property: Nothing? = null

    override fun foo(): Nothing? = null
}

fun box(): String {
    val a: A = B()
    if (a.property != null) return "FAIL 1"
    if (a.foo() != null) return "FAIL 1"
    return "OK"
}

