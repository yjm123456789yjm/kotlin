// FUNCTION_CALLED_TIMES: getPropertyCallableRef count=7
package foo

import kotlin.reflect.KMutableProperty1

// CHECK_NOT_CALLED_IN_SCOPE: scope=thisBoundPropRefA function=getPropertyCallableRef
@JsExport
open class A(var msg:String) {
    fun thisBoundPropRefA() = this::ext
}

// CHECK_NOT_CALLED_IN_SCOPE: scope=thisBoundPropRefB function=getPropertyCallableRef
@JsExport
class B:A("FromB") {

    fun thisBoundPropRefB() = this::ext
}

var global:String = ""

var A.ext:String
    get() = ":A.ext ${this.msg}:"
    set(value) { global = ":A.ext ${value}" }

var B.ext:String
    get() = ":B.ext ${this.msg}:"
    set(value) { global = ":B.ext ${value}" }

// CHECK_CALLED_IN_SCOPE: scope=box function=getPropertyCallableRef
fun box(): String {
    val a = A("Test")

    var refAExt = A::ext
    var refBExt: KMutableProperty1<B, String> = B::ext

    assertEquals("ext", refAExt.name)
    assertEquals("ext", refBExt.name)

    assertEquals(":A.ext Test:", refAExt.get(a))
    assertEquals(":B.ext FromB:", refBExt.get(B()))

    refAExt.set(a, "newA")
    assertEquals(":A.ext newA", global)

    global = ""
    refBExt.set(B(), "newB")
    assertEquals(":B.ext newB", global)

    val b = B()

    val refABoundExt = a::ext
    var refBBoundExt = b::ext

    assertEquals("ext", refABoundExt.name)
    assertEquals("ext", refBBoundExt.name)

    assertEquals(":A.ext Test:", refABoundExt.get())
    assertEquals(":B.ext FromB:", refBBoundExt.get())

    global = ""
    refABoundExt.set("newA")
    assertEquals(":A.ext newA", global)

    global = ""
    refBBoundExt.set("newB")
    assertEquals(":B.ext newB", global)

    val refABoundExt2 = a.thisBoundPropRefA()
    var refBBoundExt2 = b.thisBoundPropRefB()

    assertEquals("ext", refABoundExt2.name)
    assertEquals("ext", refBBoundExt2.name)

    assertEquals(":A.ext Test:", refABoundExt2.get())
    assertEquals(":B.ext FromB:", refBBoundExt2.get())

    global = ""
    refABoundExt2.set("newA")
    assertEquals(":A.ext newA", global)

    global = ""
    refBBoundExt2.set("newB")
    assertEquals(":B.ext newB", global)

    return "OK"
}
