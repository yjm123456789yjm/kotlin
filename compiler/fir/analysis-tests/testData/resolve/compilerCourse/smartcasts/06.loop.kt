fun test_1() {
    var x: String? = getNullableString()
    while (x != null) {
        x.length // smartcast
        x = ...
        x.length // depends on ...
    }
    x.length // no smartcast, x definitely null
}

interface A {
    fun member()
}

fun A?.foo(): A = null!!
fun A.foo(): A? = null!!

fun getCondition(): Boolean = null!!

fun test_2(a: A?) {
    var x = a
    requireNotNull(x)
    x.member() // smartcast
    while (getCondition()) {
        x.member() // no smartcast
        x = x.foo()
        x.member() // no smarcast
    }
}

class B(val x: String?)

fun B.test_3() {
    requireNotNull(x)
    x.length // smartcast
    while (...) {
        x.length // no smartcast
        var x = 10
        x = 11
    }
}

fun test_5(x: Any?) {
    run {
        x as String
    }
    x.length // smartcast
}

/*
 * callsInPlace:
 *  EXACTLY_ONCE [1, 1]
 *  AT_MOST_ONCE [0, 1]
 *  AT_LEAST_ONCE [1, inf]
 *  UNKNOWN [0 inf]
 */

fun test_6() {
    var x: String? = getNullableString()
    requireNotNull(x)
    repeat(10) {
        x.length // ???
        x = getNullableString()
        x.length // ???
    }
    x.length //
}
