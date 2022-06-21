// CHECK_BYTECODE_LISTING
// LANGUAGE: -JvmInlineValueClasses, +GenericInlineClassParameter
// IGNORE_BACKEND: JVM

inline class ICAny<T>(val value: T)

fun box(): String = ICAny("OK").value