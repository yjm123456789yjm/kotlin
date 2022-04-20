// FUNCTION_CALLED_TIMES: getCallableRef count=1

package foo

open class A {
    open fun foo(a:String,b:String): String = "fooA:" + a + b
}

class B : A() {
    override fun foo(a:String,b:String): String = "fooB:" + a + b
}

// CHECK_CALLED_IN_SCOPE: scope=box function=getCallableRef
fun box(): String {
    val b = B()
    var ref = A::foo
    val result = ref(b, "1", "2")
    return (if (result == "fooB:12") "OK" else result)
}
