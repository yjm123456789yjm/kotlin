// FUNCTION_CALLED_TIMES: getPropertyCallableRef count=7
package foo

// CHECK_NOT_CALLED_IN_SCOPE: scope=thisBoundPropRefA function=getPropertyCallableRef
@JsExport
open class A(var msg:String) {
    open var prop:String = "initA"

    fun thisBoundPropRefA() = this::prop
}

// CHECK_NOT_CALLED_IN_SCOPE: scope=thisBoundPropRefB function=getPropertyCallableRef
@JsExport
class B:A("FromB") {
    override var prop:String = "initB"

    fun thisBoundPropRefB() = this::prop
}

// CHECK_CALLED_IN_SCOPE: scope=box function=getPropertyCallableRef
fun box(): String {
    var refAProp = A::prop
    var refBProp = B::prop

    assertEquals("prop", refAProp.name)
    assertEquals("prop", refBProp.name)

    val a = A("Test")
    assertEquals("initA", refAProp.get(a))

    refAProp.set(a, "newPropA")
    assertEquals("newPropA", a.prop)

    val a1 = B()
    assertEquals("initB", refAProp.get(a1))

    refAProp.set(a1, "newPropB")
    assertEquals("newPropB", a1.prop)


    val refABoundProp = a::prop
    var refBBoundProp = a1::prop
    assertEquals("prop", refABoundProp.name)
    assertEquals("prop", refABoundProp.name)

    assertEquals("newPropA", refABoundProp.get())
    refABoundProp.set("newNewPropA")
    assertEquals("newNewPropA", a.prop)

    assertEquals("newPropB", refBBoundProp.get())
    refBBoundProp.set("newNewPropB")
    assertEquals("newNewPropB", a1.prop)

    val refABoundProp2 = a.thisBoundPropRefA()
    val refBBoundProp2 = a1.thisBoundPropRefB()
    assertEquals("prop", refABoundProp2.name)
    assertEquals("prop", refBBoundProp2.name)

    assertEquals("newNewPropA", refABoundProp2.get())
    refABoundProp2.set("newNewNewPropA")
    assertEquals("newNewNewPropA", a.prop)

    assertEquals("newNewPropB", refBBoundProp2.get())
    refBBoundProp2.set("newNewNewPropB")
    assertEquals("newNewNewPropB", a1.prop)

    return "OK"
}
