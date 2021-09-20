interface A {
    fun foo()
}

interface B {
    fun foo()
}

abstract class Foo: A, B

fun test(a: Foo) {
    a.<caret>foo()
}