/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package refined

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@ObjCRefined
annotation class MyObjCRefined

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@SwiftRefined
annotation class MySwiftRefined

@ObjCRefined
fun foo(): Int = 1

@SwiftRefined
fun fooRefined(): String = foo().toString()

@MyObjCRefined
fun myFoo(): Int = 2

@MySwiftRefined
fun myFooRefined(): String = myFoo().toString()

@ObjCRefined
val bar: Int = 3

@SwiftRefined
val barRefined: String get() = bar.toString()

@MyObjCRefined
val myBar: Int = 4

@MySwiftRefined
val myBarRefined: String get() = myBar.toString()
