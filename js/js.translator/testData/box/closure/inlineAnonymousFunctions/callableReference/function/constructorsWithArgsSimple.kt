// FUNCTION_CALLED_TIMES: getCallableRef count=2

package foo

class A(val x: Int) {
    var s = "sA:init:$x"
}

class B(val arg1: String, val arg2: String) {
    var msg = arg1 + arg2
}

// CHECK_CALLED_IN_SCOPE: scope=box function=getCallableRef
fun box(): String {
    val ref = ::A
    val result = ref(1).s + (::B)("23", "45").msg
    return (if (result == "sA:init:12345") "OK" else result)
}
