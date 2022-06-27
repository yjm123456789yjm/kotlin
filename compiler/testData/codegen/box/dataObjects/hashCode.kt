// LANGUAGE: +DataObjects
// WITH_STDLIB

package com.example

import kotlin.test.*

data object DataObject {
    data object Inner
}

fun box(): String {
    assertEquals("com.example.DataObject".hashCode(), DataObject.hashCode())
    assertEquals("com.example.DataObject.Inner".hashCode(), DataObject.Inner.hashCode())

    return  "OK"
}