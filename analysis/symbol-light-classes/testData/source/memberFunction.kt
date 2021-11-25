class A {
    fun foo(): Int {
        return 42
    }
}

fun test() {
    A().<caret>foo()
}
