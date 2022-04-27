import kotlin.contracts

fun myRun(block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTY_ONCE)
    }
    block()
}

fun myRequire(condition: Boolean) {
    contract {
        returns() implies (condition)
    }
    if (!condition) throw Exception()
}

fun <T> id(x: T): T {
    contract {
        returns(null) implies (x == null)
        returnsNotNull() implies (x != null)
    }
    return x
}

fun checkIsString(x: Any?): Boolean {
    // d1: x
    contract {
        returns(true) implies (x is A && x is B)
    }
    return x is String
}

fun test_1(y: Any?) {
    // d2: y
    // d3: checkIsString(y)

    // d3 == true -> d1 hasType A, B

    // y is A && y is B
    if (checkIsString(y)) {
        y.length
    }
    if (y is String) {
        y.length
    }
}

inline fun <T> runIf(condition: Boolean, block: () -> T): T? contract [
    callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    condition trueIn block
] {
    return if (condition) block() else null
}

inline fun <T> runIf(condition: Boolean, block: () -> T): T? {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
        condition trueIn block
    }
    // FirStubExpression()
    return if (condition) block() else null
}


fun test_2(y: String) {
    val x = runIf(y != null) { y.length }
}
