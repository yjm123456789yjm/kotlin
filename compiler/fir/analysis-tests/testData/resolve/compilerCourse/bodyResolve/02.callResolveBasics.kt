fun <K> id(x: K): K = x // (1)

fun id(x: Int): Int = x // (2)

class A {
    fun test(y: String) {
        id/*<String>*/(y) // : String
        // resolved to id(x: K): K
        // parameter x: K -> argument y: String
        // K == String
        // id(y): String
    }

    fun id(): String = TODO() // (3)
}

/*
 * 1. resolve arguments and explicit receiver
 * 2. find declaration with specific name
 * 3. understand if found function is applicable
 * 4. if not return to 2.
 * 5. infer all generics if function has some
 * 6. set type arguments and return type
 *
 * 1-4: call resolution/resolve
 *     find candidate (matching function/property/etc)
 * 5-6: call completion
 *     infer generics
 */

fun <T> materialize(): T = null!!

fun test_2() {
    val x: String = id(materialize())
    /*
     * from materialize:
     *    generic: T
     * from id:
     *   generic: K
     *   T <: K
     *   K <: String
     *
     *   T == String
     *   K == String
     */

    val y = id(materialize())
    /*
     * for (1):
     *   T <: K
     *
     * for (2):
     *   T <: Int
     */
}
