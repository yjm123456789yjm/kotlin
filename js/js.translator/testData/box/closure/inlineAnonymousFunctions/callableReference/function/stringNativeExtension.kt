// KJS_WITH_FULL_RUNTIME

package foo

// CHECK_CALLED_IN_SCOPE: scope=box function=getCallableRef
fun box(): String {
    var s = "abc"
    assertEquals("ABC", (String::toUpperCase)(s))

    return "OK"
}
