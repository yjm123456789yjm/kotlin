interface A {
    fun a() {}
}
class Foo : A {
    inner class Foo : A {
        fun foo() {
            super@F<caret>oo.a()
        }
    }
}