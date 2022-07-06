// LAMBDAS: INDY
// WITH_STDLIB

import kotlin.jvm.JvmSerializableLambda
import kotlin.test.assertEquals
import kotlin.jvm.internal.FunctionBase

fun test(f: Function<*>, arity: Int) {
    assertEquals(arity, (f as FunctionBase).arity)
}

fun foo(s: String, i: Int) {}
class A {
    fun bar(s: String, i: Int) {}
}
fun Double.baz(s: String, i: Int) {}

fun box(): String {
    test(::foo, 2)
    test(A::bar, 3)
    test(Double::baz, 3)

    test(::box, 0)

    fun local(x: Int) {}
    test(::local, 1)

    test(@JvmSerializableLambda fun(s: String) = s, 1)
    test(@JvmSerializableLambda fun(){}, 0)
    test(@JvmSerializableLambda {}, 0)
    test(@JvmSerializableLambda {x: Int -> x}, 1)

    return "OK"
}
