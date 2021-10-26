//!LANGUAGE: +DefinitelyNonNullableTypes
// IGNORE_BACKEND_FIR: ANY

fun <T> asFoo(t: T) = t as (T & Any)
fun <T> safeAsFoo(t: T) = t as? (T & Any)