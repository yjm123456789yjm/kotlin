@CompileTimeCalculation
enum class Numbers(val number: Int) {
    ONE(1), TWO(2), THREE(3)
}

@PartialEvaluation
inline fun enumCount(): Int {
    return Numbers.values().size
}

@PartialEvaluation
inline fun getListOfNumbers(): List<Int> {
    return Numbers.values().map { it.number }
}

@PartialEvaluation
inline fun getNumber(enum: Numbers): Int {
    return enum.number
}

@CompileTimeCalculation
public inline fun <T, R> Array<out T>.map(transform: (T) -> R): List<R> {
    val destination = ArrayList<R>(size)
    for (item in this) destination.add(transform(item))
    return destination
}

val a = enumCount()
val b = getListOfNumbers()
val c1 = getNumber(Numbers.ONE)
val c2 = getNumber(Numbers.TWO)
