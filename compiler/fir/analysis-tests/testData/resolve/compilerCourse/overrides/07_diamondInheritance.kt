interface A {
    fun test_1()
    fun test_2()
    fun test_3()
    fun test_4()
}

interface B : A {
    override fun test_1()
    override fun test_2()
    override fun test_4()
}

interface C : A {
    override fun test_1()
    override fun test_4()
}

interface D : B, C {
    override fun test_1() // overrides B.test_1() and C.test_1()
}

fun test_1(d: D) {
    d.test_1() // D.test_1()
    d.test_2() // B.test_2()
    d.test_3() // A.test_3()
    d.test_4() // [IO] D.test_4() from B.test_4() and C.test_4()
}
