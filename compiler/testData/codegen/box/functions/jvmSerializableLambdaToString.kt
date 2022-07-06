// LAMBDAS: INDY
// WITH_STDLIB
// WITH_REFLECT

import kotlin.jvm.JvmSerializableLambda

fun check(expected: String, obj: Any?) {
    val actual = obj.toString()
    if (actual != expected)
        throw AssertionError("Expected: $expected, actual: $actual")
}

fun box(): String {
    check("() -> kotlin.Unit",
          @JvmSerializableLambda { -> })
    check("() -> kotlin.Int",
          @JvmSerializableLambda { -> 42 })
    check("(kotlin.String) -> kotlin.Long",
          @JvmSerializableLambda fun (s: String) = 42.toLong())
    check("(kotlin.Int, kotlin.Int) -> kotlin.Unit",
          @JvmSerializableLambda { x: Int, y: Int -> })

    check("kotlin.Int.() -> kotlin.Unit",
          @JvmSerializableLambda fun Int.() {})
    check("kotlin.Unit.() -> kotlin.Int?",
          @JvmSerializableLambda fun Unit.(): Int? = 42)
    check("kotlin.String.(kotlin.String?) -> kotlin.Long",
          @JvmSerializableLambda fun String.(s: String?): Long = 42.toLong())
    check("kotlin.collections.List<kotlin.String>.(kotlin.collections.MutableSet<*>, kotlin.Nothing) -> kotlin.Unit",
          @JvmSerializableLambda fun List<String>.(x: MutableSet<*>, y: Nothing) {})

    check("(kotlin.IntArray, kotlin.ByteArray, kotlin.ShortArray, kotlin.CharArray, kotlin.LongArray, kotlin.BooleanArray, kotlin.FloatArray, kotlin.DoubleArray) -> kotlin.Array<kotlin.Int>",
          @JvmSerializableLambda fun (ia: IntArray, ba: ByteArray, sa: ShortArray, ca: CharArray, la: LongArray, za: BooleanArray, fa: FloatArray, da: DoubleArray): Array<Int> = null!!)

    check("(kotlin.Array<kotlin.Array<kotlin.Array<kotlin.collections.List<kotlin.String>>>>) -> kotlin.Comparable<kotlin.String>",
          @JvmSerializableLambda fun (a: Array<Array<Array<List<String>>>>): Comparable<String> = null!!)

    return "OK"
}
