// WITH_STDLIB
// IGNORE_BACKEND: JVM
// WORKS_WHEN_VALUE_CLASS
// LANGUAGE: +ValueClasses, +GenericInlineClassParameter

OPTIONAL_JVM_INLINE_ANNOTATION
value class X<T: Any>(val x: T)

fun useX(x: X<String>): String = x.x

fun <T> call(fn: () -> T) = fn()

fun box() = useX(call(fun(): X<String> { return X("OK") }))