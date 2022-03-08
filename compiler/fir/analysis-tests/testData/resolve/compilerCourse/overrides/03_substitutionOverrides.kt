interface A<T> {
    // dispatch receiver: A<T>
    fun test_1(): T // (1)
    fun test_2(x: T) // (2)
    fun test_3() // (3)
}

interface B<K> : A<K> {
    override fun test_1(): K // (4) overrides [SO] A<K>.test_1(): K, not (1)
    // test_2 in Use-site scope of B<K>:
    // [SO] B<K>.test_2(x: K) (5)
}

interface C : B<String> {
    // supertype scope: use-site scope of B<String>
    override fun test_2(x: String) // (6) overrides B<String>.test_2(x: String)
}

fun <K> test_1(a: A<List<K>>, x: List<K>) {
    a.test_1() // A<T>.test_1(): T (1), { T -> List<K> }
    a.test_2(x) // (2)
    a.test_3() // (3)
}

fun <T> test_2(b: B<T>, x: T) {
    b.test_1() // (4)
    b.test_2(x) // (5)
    b.test_3() // [SO] B<K>.test_3()
}

fun test_3(c: C, x: String) {
    c.test_1() // [SO] C.test_1(): String
    c.test_2(x) // (6)
    c.test_3() // [SO] C.test_3()
}
