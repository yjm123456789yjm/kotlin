fun test_1(x: Any, y: Any) {
    when {
        x is String -> "x"
        y is Int -> "y"
        else -> "nothing"
    }
}

fun test_2(x: Any) {
    when (x) {
        is Int -> "Int: $x"
        1 -> "One"
        null -> "null"
        else -> "nothing"
    }
}

fun getAny(): Any? = null

fun test_2() {
    when (val y = getAny()) {
        is Int -> "Int: $y"
        1 -> "One"
        null -> "null"
        else -> "nothing"
    }
}
