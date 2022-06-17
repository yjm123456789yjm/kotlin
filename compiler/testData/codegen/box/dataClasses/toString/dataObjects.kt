// LANGUAGE: +DataObjects
// WITH_STDLIB

package com.example

import kotlin.test.*

data object DataObject {
    data object Nested
}

class Foo {
    data object Inner
}

data object Bar {
    override fun toString() = "Overriden"
}

open class A {
    override fun toString() = "A"
}

data object B: A()

open class X {
    final override fun toString() = "X"
}

data object Y: X()

class C {
    companion object CC
}

fun box(): String {
    assertEquals("DataObject", DataObject.toString())
    assertEquals("Nested", DataObject.Nested.toString())
    assertEquals("Inner", Foo.Inner.toString())
    assertEquals("Overriden", Bar.toString())
    assertEquals("B", B.toString())
    assertEquals("X", Y.toString())
    assertNotEquals("CC", C.CC.toString())

    return "OK"
}