// SKIP_TXT

infix fun Int.suspend(c: () -> Unit) { c() }

fun bar() {
    1 suspend fun() {
        println()
    }

    1 @Ann suspend fun() {
        println()
    }

    1 suspend @Ann fun() {
        println()
    }
}

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

fun main(suspend: WLambdaInvoke) {
    1 suspend fun() {}
}

class WLambdaInvoke {
    operator fun Int.invoke(l: () -> Unit) {}
}