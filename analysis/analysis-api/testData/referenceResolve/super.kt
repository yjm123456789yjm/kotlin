interface A {
    fun a() {}
}
class Foo : A {
    fun foo() {
        s<caret>uper.a()
    }
}