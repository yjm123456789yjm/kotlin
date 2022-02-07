fun test_1(x: Int, y: Int) {
    val a = x + y
    val b = x.plus(y)
}

fun test_2(b1: Boolean, b2: Boolean) {
    val and1 = b1 && b2
    val or1 = b1 || b2

    val and2 = b1.and(b2)
    val or2 = b1.or(b2)
}

fun test_3(x: String?) {
    val s = x ?: "hello"
}

fun test_4(x: Int, y: Int) {
    val a = x < y
}

fun test_5(x: String, y: String) {
    val a = x == y
}
