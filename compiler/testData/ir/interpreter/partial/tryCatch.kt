@PartialEvaluation
inline fun withTryCatch(flag: Boolean) {
    return try {
        if (flag) "OK" else "Fail"
    } catch (e: Exception) {
        if (flag) "OK" else "Fail"
    } finally {
        if (flag) "OK" else "Fail"
    }
}

@PartialEvaluation
inline fun mutableVar(): Int {
    var a = 0
    try {
        a = 1
    } catch (e: Exception) {
        a = 2
    } finally {
        a = 3
    }
    return a // must be unknown
}

val a = withTryCatch(true)
val b = mutableVar()
