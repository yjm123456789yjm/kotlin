fun foo(): Int {
    return 42
}

fun bar() {
    <caret>foo().toString()
}
