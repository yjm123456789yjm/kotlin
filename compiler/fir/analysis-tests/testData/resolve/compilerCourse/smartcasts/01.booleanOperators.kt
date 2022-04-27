fun test_0(x: Any) {
    /*
     * d1: x is String
     * d2: x
     * d1 == true => d2 hasType String
     *
     * approve d1 == true
     *
     * flow: d2 hasType String
     */

    if (x is String) {
        x.length
    }
}

fun test_1(b1: Boolean, b2: Boolean) {
    /*
     * d1: b1
     * d2: b2
     * d3: b1 && b2
     *
     * d1 == false => TS1
     * d2 == false => TS2
     *
     * d3 == true => d1 == true
     * d3 == true => d2 == true
     * d3 == false => TS1 or TS2
     */

    if (b1 && b2) {
        // ???
    } else {
        // ???
    }
}

interface A { fun foo() }
interface B { fun bar() }
interface C : A, B
interface D : A, B

fun test_1_1(x: Any) {
    if (x !is C && x !is D) {

    } else {
        // TS: (x hasType C) or (x hasType D) -> (x hasType A, B)
        x.foo()
        x.bar()
    }
}

fun test_2(b1: Boolean, b2: Boolean) {
    /*
     * d1: b1
     * d2: b2
     * d3: b1 || b2
     *
     * d1 == true => TS1
     * d2 == true => TS2
     *
     * d3 == true => TS1 or TS2
     * d3 == false => d1 == false
     * d3 == false => d2 == false
     */
    if (b1 || b2) {
        // ???
    } else {
        // ???
    }
}

fun test_3(b: Boolean, x: Any) {
    if (!b) {
        // ???
    }

    val b2 = !(x is String)
    /*
     * d1: x is String
     * d2: !(x is String)
     *
     * d1 == true -> ST1
     * d1 == false -> ST2
     *
     * d2 == true -> ST2
     * d2 == false -> ST1
     */
}
