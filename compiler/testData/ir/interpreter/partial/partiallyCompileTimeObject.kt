@PartialEvaluation
public inline fun <T> T.apply(block: T.() -> Unit): T {
    block()
    return this
}

class A @CompileTimeCalculation constructor(var a: Int) {
    fun inc() {
        a++
    }

    @CompileTimeCalculation
    fun compileTimeInc(): Boolean {
        a++
        return true
    }
}

@PartialEvaluation
inline fun getA(obj: A): Int {
    return obj.a
}

@PartialEvaluation
inline fun incAndGetA(obj: A): Int {
    obj.inc()
    return obj.a // can't evaluate because `inc` is not compile time fun
}

@PartialEvaluation
inline fun dontReplaceCallWithSideEffect(obj: A): Boolean {
    // can't replace this call, because inside of it there is a mutable operation (side effect)
    return obj.compileTimeInc()
}

val a = getA(A(10))
val b = incAndGetA(A(10))
val c = dontReplaceCallWithSideEffect(A(10))
val d = A(10).apply { dontReplaceCallWithSideEffect(this) }
