interface A {
    fun test_1() // (1)
    fun test_2() // (2)
}

interface B : A {
    override fun test_1() // overrides A.test_1
    override fun test_2() // (4)
}

interface C : A, B {
    override fun test_1() // overrides B.test_1
}

fun test_1(c: C) {
    c.test_1() // C.test_1
    c.test_2() // B.test_2
}
