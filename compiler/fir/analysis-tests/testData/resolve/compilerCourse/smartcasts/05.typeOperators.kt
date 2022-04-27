fun test_1(x: Any?) {
    x as String

    /*
     * d1: x
     * d2: x as String
     *
     * d1 hasType String
     */
}

fun test_2(x: Any?) {
    x as? String

    /*
     * d1: x
     * d2: x as? String
     *
     * d2 != null => d1 hasType String
     */

}

fun test_3(x: String?) {
    x!!
    x.length // smartcast
    /*
     * d1: x
     * d2: x!!
     *
     * d1 hasType Any => x: (String? & Any) -> x: String
     */
}
