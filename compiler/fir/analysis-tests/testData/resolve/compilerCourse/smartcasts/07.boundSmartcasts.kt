fun test_1(x: Any?) {
    /*
     * d1: x
     * d2: y
     * d2 alias d1
     */
    val y = x
    if (x is String) {
        x.length
        y.length
    }
    if (y is String) {
        /*
         * d1 hasType String
         */
        x.length
        y.length
    }
}

fun test_2(x1: Any?, x2: Any?) {
    /*
     * d1: x1
     * d2: x2
     * d3: y
     */
    val y: Any?
    if (someCondition) {
        y = x1
        // d3 aliases d1
        if (y is String) {
            x1.length
            y.length
        }
    } else {
        y = x2
        // d3 aliases d2
        if (y is String) {
            x2.length
            y.length
        }
    }
    // d3 hasType CST(x1, x2)
    // d3 not aliases nothing
    if (y is String) {
        y.length
        x1.length // ???
        x2.length // ???
    }
}

fun test_3() {
    var x: String? = null!! // d1
    var y: String? = null!! // d2

    y = x // d2 aliases d1
    reqiureNotNull(y)
    // d1 hasType Any
    x.length // smartcast
    y.length // smartcast

    x = getNullableString()
    // d2 not aliases d1
    // d2 has all TS from d1
    y.length
}
