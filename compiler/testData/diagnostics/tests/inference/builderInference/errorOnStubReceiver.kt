// WITH_STDLIB
// FIR_DUMP
fun Any?.test() {}

class Bar {
    fun test() {}
}

fun main() {

    buildList {
        add(Bar())
        <!BUILDER_INFERENCE_STUB_RECEIVER!>this.get(0)<!>.test() // resolved to Any?.test
    }
    buildList<Bar> {
        add(Bar())
        this.get(0).test() // resolved to Bar.test
    }
}
