interface A<T> {
    fun takeT(t: T)
}

abstract class Foo: A<String>

fun test(a: Foo, s: String) {
    a.<caret>takeT(s)
}