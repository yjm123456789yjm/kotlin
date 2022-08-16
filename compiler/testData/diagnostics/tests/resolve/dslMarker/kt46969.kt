// FIR_IDENTICAL
// WITH_STDLIB
@DslMarker
annotation class Foo

@Foo
interface Scope<T> {
    fun value(value: T)
}

fun foo(block: Scope<Nothing>.() -> Unit) {}

inline fun <reified T> Scope<*>.nested(noinline @<!OPT_IN_USAGE_ERROR!>BuilderInference<!> block: Scope<T>.() -> Unit) {}
inline fun <reified K> Scope<*>.nested2(noinline @<!OPT_IN_USAGE_ERROR!>BuilderInference<!> block: Scope<K>.() -> Unit) {}


fun main() {
    foo {
        nested {
            value(1)

            nested2 {
                value("foo")
            }
        }
    }
}