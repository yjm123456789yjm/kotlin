class C(val value: String) {
    fun getField() = value
    fun getMethod() {}
}

fun foo(): Any {
    var result: Any = ""
    result = C("OK")
    try {
        result = result.getField()
    } catch (e: Exception) {
        result.getMethod()
    }
    return result
}


fun box() = foo().toString()
