interface A {
    fun test_1()
    fun test_2(): Number
}

interface B {
    fun test_1()
    fun test_2(): Int
}

interface C : A, B {
    override fun test_1() // overrides A.test_1() and B.test_1()
    // [IO] C.test_2(): Int overrides A.test_2() and B.test_2()
}

fun test_1(c: C) {
    c.test_1() // C.test_1()
    c.test_2() // [IO] C.test_2(): Number
}
