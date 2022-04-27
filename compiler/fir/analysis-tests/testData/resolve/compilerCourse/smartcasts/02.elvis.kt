fun test_1(x: String?, y: String) {
    x ?: y // ???
    /*
     * d1: x
     * d2: y
     * d3: x ?: y
     *
     * d3 != null => (d1 != null) or (d2 != null)
     * d3 == null => (d1 == null) and (d2 == null)
     */
}

fun test_2(x: String?) {
    x ?: return
    /*
     * d1: x
     *
     * approved: d1 != null
     */
}
