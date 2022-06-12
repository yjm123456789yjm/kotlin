// SKIP_TXT
// ISSUE: KT-52684

fun test(x: Int, y: Int) {
    if (x <!UNRESOLVED_REFERENCE!><<!> (if (y >= 115) 1 else 2)) {
        Unit
    }
}
