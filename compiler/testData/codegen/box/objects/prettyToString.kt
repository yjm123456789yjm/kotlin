// TARGET_BACKEND: JVM
// LANGUAGE: +PrettyToStringForObjects
// WITH_STDLIB

package com.example

import kotlin.test.*

object DataObject {
    object Inner
}

object O {
    override fun toString() = "Overriden"
}

open class A {
    override fun toString() = "A"
}

object B: A()

fun box(): String {
    assertEquals("DataObject", DataObject.toString())
    assertEquals("Inner", DataObject.Inner.toString())
    assertEquals("Overriden", O.toString())
    assertEquals("B", B.toString())

    return "OK"
}