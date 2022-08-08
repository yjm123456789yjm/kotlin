// FIR_IDENTICAL
@RequiresOptIn
annotation class Marker

@Marker
interface I1

@SubclassOptInRequired(Marker::class)
interface I2

// Should not be enough, and should not depend on the order of I1, I2
@SubclassOptInRequired(Marker::class)
interface Impl1 : <!OPT_IN_USAGE_ERROR!>I1<!>, I2

@SubclassOptInRequired(Marker::class)
interface Impl2 : I2, <!OPT_IN_USAGE_ERROR!>I1<!>

@Marker
class C

open class D {
    @Marker
    open fun bar() {}
}

@SubclassOptInRequired(Marker::class)
open class E : D() {
    init {
        val c = <!OPT_IN_USAGE_ERROR!>C<!>()
        D().<!OPT_IN_USAGE_ERROR!>bar<!>()
    }

    fun foo() {
        val c = <!OPT_IN_USAGE_ERROR!>C<!>()
        D().<!OPT_IN_USAGE_ERROR!>bar<!>()
    }

    override fun <!OPT_IN_OVERRIDE_ERROR!>bar<!>() {

    }
}

@SubclassOptInRequired(Marker::class)
interface Foo {
    fun foo() = Unit
}

<!NOTHING_TO_INLINE!>inline<!> fun inlineFoo() {
    object : <!OPT_IN_USAGE_ERROR!>Foo<!> { }
}

fun test() {
    // call site
    inlineFoo()
}
