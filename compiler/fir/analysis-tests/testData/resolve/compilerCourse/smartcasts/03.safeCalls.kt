class A1(val b: B1) {
    fun foo() {}
}
class B1(val s: String?)

fun test_1(a: A1?) {
    if (a?.b?.s != null) {
        a.foo() // smartcast
    }

    /*
     * d1: a
     * d2: a?.b
     * d3: a?.b.s
     *
     * d3 != null => d2 != null => d1 != null => d1 hasType Any
     *
     */
}

class A2(val b: B2?) {
    fun foo() {}
}
class B2(val s: String?) {
    fun bar() {}
}

fun test_2(a: A2?) {
    if (a?.b?.s != null) {
        a.foo()
        a.b.bar()
    }

    /*
     * d1: a
     * d2: a?.b
     * d3: a?.b?.s
     *
     * d4: a.b
     *
     * d3 != null => d2 != null => d1 != null => d1 hasType Any
     *                                           d4 hasType Any
     */
}

class A3 {
    fun foo() {}
}

fun A3?.id(): A3? = TODO()

fun test_3(a: A3?) {
    if (a?.id()?.id()?.id() != null) {
        a.foo() // no smartcast
    }

    if (a?.id().id()?.id() != null) {
        a.foo() // no smartcast
    }
}

interface A4() {
    val b: B4?
    fun foo() {}
}

interface B4 {
    val s: String?
    fun bar() {}
}

class B4Impl(override val s: String?) : B4 {}

class A4Impl : A4 {
    override val b: B4
        get() = if (getRandomBoolean()) B4Impl() else null

    fun getRandomBoolean(): Boolean = TODO()
}

fun test_4(a: A4?) {
    if (a?.b?.s != null) {
        a.foo() // smartcast
        a.b.bar() // no smartcast, a.b is unstable
    }

    /*
     * d1: a
     * d2: a?.b
     * d3: a?.b.s
     *
     * d3 != null => d2 != null => d1 != null => d1 hasType Any
     *
     */
}

/*
 * stable:
 *  - local properties
 *  - value parameters
 *  - final val properties of same module
 */

// MODULE: lib_ver_1
class A(val x: String?)

// MODULE: lib_ver_2
class A(var x: String?)

// MODULE: main(lib)
// compile classpath: lib_ver_1
// runtime classpath: lib_ver_2

fun test(a: A) {
    if (a.x != null) {
        a.x.length // smartcast?
    }
}
