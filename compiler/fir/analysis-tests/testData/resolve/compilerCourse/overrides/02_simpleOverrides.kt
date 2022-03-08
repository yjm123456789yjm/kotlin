abstract class A {
    open fun test_1(): String = "hello" // (1)
    abstract fun test_2() // (2)
}

class B : A() {
    override fun test_2() {} // (3) overrides A.test_2 (2)
}

fun test_1(a: A, b: B) {
    a.test_1() // (1)
    a.test_2() // (2)

    b.test_1() // (1)
    b.test_2() // (3)
}

// ------------------------------------------------------

interface C {
    fun test_1(): Number // (1)

    fun test_2(x: Int) // (2)
    fun test_2(y: Double) // (3)

    fun test_3(x: Number, y: Int) // (4)
}

interface D : C {
    override fun test_1(): Int // overrides (1)

    override fun test_2(x: Number) // nothing to override

    override fun test_3(y: Int, x: Number) // nothing to override
}

/*
 * test_2 in use-site scope of D:
 *   - C.test_2(Int)
 *   - C.test_2(Double)
 *   - D.test_2(Number)
 */
