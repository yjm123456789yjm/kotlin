fun test_1(list: MutableList<String>) {
    val x = list[10]
    list[10] = "hello"
}

fun test_2(m: Matrix3<String>) {
    val x = m[1, 2, 1]
    m[1, 2, 1] = "hello"
}
