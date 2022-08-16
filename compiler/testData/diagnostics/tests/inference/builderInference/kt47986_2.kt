// !RENDER_DIAGNOSTICS_FULL_TEXT
// WITH_STDLIB

import kotlin.experimental.ExperimentalTypeInference

class Foo<K>

@OptIn(ExperimentalTypeInference::class)
fun <K> buildFoo(@BuilderInference builderAction: Foo<K>.() -> Unit): Foo<K> = Foo()

fun <L> Foo<L>.bar() {}

fun <K> id(x: K) = x

fun main() {
    val x = <!INFERRED_INTO_DECLARED_UPPER_BOUNDS!>buildFoo<!> { // can't infer
        val y = id(::bar)
    }
}