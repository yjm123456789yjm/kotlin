fun foo() {}

fun box(): String {
    foo().equals(Unit)
    return "OK"
}