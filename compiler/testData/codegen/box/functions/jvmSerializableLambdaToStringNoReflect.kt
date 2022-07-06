// LAMBDAS: INDY
// WITH_STDLIB

import kotlin.jvm.JvmSerializableLambda

fun check(expected: String, obj: Any?) {
    val actual = obj.toString()
    if (actual != expected)
        throw AssertionError("Expected: $expected, actual: $actual")
}

fun box(): String {
    check("Function0<kotlin.Unit>",
          @JvmSerializableLambda { -> })
    check("Function0<java.lang.Integer>",
          @JvmSerializableLambda { -> 42 })
    check("Function1<java.lang.String, java.lang.Long>",
          @JvmSerializableLambda fun (s: String) = 42.toLong())
    check("Function2<java.lang.Integer, java.lang.Integer, kotlin.Unit>",
          @JvmSerializableLambda { x: Int, y: Int -> })

    check("Function1<java.lang.Integer, kotlin.Unit>",
          @JvmSerializableLambda fun Int.() {})
    check("Function1<kotlin.Unit, java.lang.Integer>",
          @JvmSerializableLambda fun Unit.(): Int? = 42)
    check("Function2<java.lang.String, java.lang.String, java.lang.Long>",
          @JvmSerializableLambda fun String.(s: String?): Long = 42.toLong())
    check("Function3<java.util.List<? extends java.lang.String>, java.util.Set<?>, ?, kotlin.Unit>",
          @JvmSerializableLambda fun List<String>.(x: MutableSet<*>, y: Nothing) {})

    check("Function8<int[], byte[], short[], char[], long[], boolean[], float[], double[], java.lang.Integer[]>",
          @JvmSerializableLambda fun (ia: IntArray, ba: ByteArray, sa: ShortArray, ca: CharArray, la: LongArray, za: BooleanArray, fa: FloatArray, da: DoubleArray): Array<Int> = null!!)

    check("Function1<java.util.List<? extends java.lang.String>[][][], java.lang.Comparable<? super java.lang.String>>",
          @JvmSerializableLambda fun (a: Array<Array<Array<List<String>>>>): Comparable<String> = null!!)

    return "OK"
}
