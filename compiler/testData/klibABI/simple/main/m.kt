
fun box(): String {
    try {
        return qux(true)
    } catch(ex: Throwable) {
        return "OK"
    }

    return "FAIL2"
}