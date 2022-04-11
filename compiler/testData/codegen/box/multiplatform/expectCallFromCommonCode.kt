// !LANGUAGE: +MultiPlatformProjects
// TARGET_BACKEND: JVM_IR
// WITH_STDLIB

// MODULE: m1-common
// FILE: common.kt

expect fun regularFun(): String
expect fun <T> genericFun(): T

expect open class RegularBase {
    fun finalFun(): String

    open fun openFun(): CharSequence
}

expect class RegularDerived : RegularBase {
    override fun openFun(): String
}

expect open class GenericBase<T> {
    fun regularFinalFun(): String
    open fun regularOpenFun(): CharSequence

    fun genericFinalFun(): T
    open fun genericOpenFun(): T
}

expect class GenericDerived<T> : GenericBase<T> {
    override fun regularOpenFun(): String
    override fun genericOpenFun(): T
}

expect class SpecificDerived : GenericBase<String> {
    override fun regularOpenFun(): String
    override fun genericOpenFun(): String
}

fun boxImpl(
    regularBase: RegularBase,
    regularDerived: RegularDerived,
    genericBase: GenericBase<String>,
    genericDerived: GenericDerived<String>,
    specificDerived: SpecificDerived
): String {
    return regularFun() +
            genericFun<String>() +

            regularBase.finalFun() +
            regularBase.openFun() +

            regularDerived.finalFun() +
            regularDerived.openFun() +

            genericBase.regularFinalFun() +
            genericBase.regularOpenFun() +
            genericBase.genericFinalFun() +
            genericBase.genericOpenFun() +

            genericDerived.regularFinalFun() +
            genericDerived.regularOpenFun() +
            genericDerived.genericFinalFun() +
            genericDerived.genericOpenFun() +

            specificDerived.regularFinalFun() +
            specificDerived.regularOpenFun() +
            specificDerived.genericFinalFun() +
            specificDerived.genericOpenFun()
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt

actual fun regularFun(): String = "1(R)"
actual fun <T> genericFun(): T = "1(G)" as T

actual open class RegularBase {
    actual fun finalFun(): String = "1(RB)"

    actual open fun openFun(): CharSequence = "2(RB)"
}

actual class RegularDerived : RegularBase() {
    actual override fun openFun(): String = "2(RD)"
}

actual open class GenericBase<T> {
    actual fun regularFinalFun(): String = "1(GB)"
    actual open fun regularOpenFun(): CharSequence = "2(GB)"

    actual fun genericFinalFun(): T = "3(GB)" as T
    actual open fun genericOpenFun(): T = "4(GB)" as T
}

actual class GenericDerived<T> : GenericBase<T>() {
    actual override fun regularOpenFun(): String = "2(GD)"
    actual override fun genericOpenFun(): T = "4(GD)" as T
}

actual class SpecificDerived : GenericBase<String>() {
    actual override fun regularOpenFun(): String = "2(SD)"
    actual override fun genericOpenFun(): String = "4(SD)"
}

const val EXPECTED = "1(R)1(G)1(RB)2(RB)1(RB)2(RD)1(GB)2(GB)3(GB)4(GB)1(GB)2(GD)3(GB)4(GD)1(GB)2(SD)3(GB)4(SD)"

fun box(): String {
    val result = boxImpl(
        RegularBase(),
        RegularDerived(),
        GenericBase(),
        GenericDerived(),
        SpecificDerived()
    )
    return if (result == EXPECTED) {
        "OK"
    } else {
        buildString {
            appendLine("FAIL:")
            appendLine("Expected: $EXPECTED")
            appendLine("  Actual: $result")
        }
    }
}
