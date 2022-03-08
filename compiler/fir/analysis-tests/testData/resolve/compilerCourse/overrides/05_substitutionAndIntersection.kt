interface A {
    fun test_1(): String // (1)
    fun test_2(): String // (2)
    fun test_3(): String
}

interface B<T> {
    fun test_1(): T // (3)
    fun test_2(): T // (4)
}

interface C : A, B<String> {
    override fun test_1(): String // overrides A.test_1(): String and [SO] B<String>.test_1(): String
    // [IO] C.test_2(): String overrides A.test_2(): String and [SO] B<String>.test_2(): String
}

fun test_1(c: C) {
    c.test_1() // ???
    c.test_2() // ???
    c.test_3() // A.test_3(): String
}
