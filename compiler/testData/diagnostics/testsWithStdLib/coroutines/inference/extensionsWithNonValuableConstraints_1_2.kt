// FIR_IDENTICAL
// !LANGUAGE: -ExperimentalBuilderInference
// !DIAGNOSTICS: -UNUSED_PARAMETER

interface Base

interface Controller<T> : Base {
    suspend fun yield(t: T) {}
}

fun <S> generate(@<!OPT_IN_USAGE_ERROR!>BuilderInference<!> g: suspend Controller<S>.() -> Unit): S = TODO()

suspend fun Base.baseExtension() {}

val test1 = generate {
    yield("foo")
    baseExtension()
}
