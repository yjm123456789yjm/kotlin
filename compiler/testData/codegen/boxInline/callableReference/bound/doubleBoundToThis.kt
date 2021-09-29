
// KT-47767

public typealias LoggingFunctionType<T> = () -> T

class LLoggerTest {
    private var i = 0
    fun testDebugTag(): String {
        return testLoggingPassThough(
            ::forRef
        )
    }
    private fun forRef(): String {
        if (i == 0) {
            i++
            return "O"
        }
        return "K"
    }
}

private inline fun testLoggingPassThough(loggerMethod: LoggingFunctionType<String>): String {
    return loggerMethod() + loggerMethod() // if this call is commented the issue doesn't reproduce
}


fun box() = "OK"