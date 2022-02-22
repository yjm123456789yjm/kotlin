// LANGUAGE: +MultiPlatformProjects
// TARGET_BACKEND: JVM_IR
// MODULE: m1-common
// FILE: common.kt

abstract class Base<T> {
    fun done(): String = "OK"

    abstract fun foo(): String
}

class Foo<K> {
    fun base(): Base<K> = object : Base<K>() {
        override fun foo(): String {
            return done() // substitution override (1)
            // substitution override (2)
        }
    }
}

// MODULE: m1-jvm()()(m1-common)
// FILE: main.kt

fun box() = Foo<String>().base().done() // substitution override (2)


