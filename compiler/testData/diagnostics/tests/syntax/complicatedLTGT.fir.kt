// SKIP_TXT
// ISSUE: KT-8263

fun test(x: Int, y: Int) {
    if (x <!UNRESOLVED_REFERENCE!><<!> (if (y > 115) 1 else 2)) {
        Unit
    }
}
