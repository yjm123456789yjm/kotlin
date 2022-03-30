fun baz(): String {
    // ... 100 lines of code
    return "world"
}

/*
 * Expressions:
 *   - property access/object reference      | reference (to some symbol), receiver
 *   - function calls                        | reference, arguments, receiver
 *   - if/when/try                           | branches, condition, exhaustiveness
 *   - literals (numbers and strings)        |
 *   - lambdas and anonymous functions       | body, receiver and `it` parameter
 *   - anonymous objects                     | all phases from supertypes to body resolve
 *   - callable reference                    | reference
 *   - prefix/postfix increment/decrement    | reference
 *
 * Statements:
 *   - local property declarations           | type, initializer, delegate
 *   - for (same as while)/while/do while    | condition, body
 *   - named objects/local classes           | all phases from supertypes to body resolve
 *   - local functions                       | types in signature, body
 *   - assignments                           | lhs and rhs
 */

// lambda: (T) -> R
//       U.(T) -> R

fun foo(x: Any) {
    if (x is String) {
        x.length
    }
}

object Foo

// to know type of bar()
// we need to know type of foo()
fun bar() = run {

    fun test(x: String): Int {
        return 10
    }

    o?.a[1] += b

    val a by lazy { A() }

    val x = y++
    foo() + baz()
}

fun foo(a: A) {
    "hello".let { it.length }

    val x = when (a) {
        is B -> 1
        is C -> 2
//        else -> ???
    }
}

open class A
class B : A()
class C : A()

class Foo {
    fun foo() = 10
}

fun foo() = bar()
fun bar() = foo()
