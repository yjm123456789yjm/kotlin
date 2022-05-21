/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package refined

@ExperimentalObjCRefinement
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@ObjCRefined
annotation class MyObjCRefined

@ExperimentalObjCRefinement
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@SwiftRefined
annotation class MySwiftRefined

@OptIn(ExperimentalObjCRefinement::class)
@ObjCRefined
fun foo(): Int = 1

@OptIn(ExperimentalObjCRefinement::class)
@SwiftRefined
fun fooRefined(): String = foo().toString()

@OptIn(ExperimentalObjCRefinement::class)
@MyObjCRefined
fun myFoo(): Int = 2

@OptIn(ExperimentalObjCRefinement::class)
@MySwiftRefined
fun myFooRefined(): String = myFoo().toString()

@OptIn(ExperimentalObjCRefinement::class)
@ObjCRefined
val bar: Int = 3

@OptIn(ExperimentalObjCRefinement::class)
@SwiftRefined
val barRefined: String get() = bar.toString()

@OptIn(ExperimentalObjCRefinement::class)
@MyObjCRefined
val myBar: Int = 4

@OptIn(ExperimentalObjCRefinement::class)
@MySwiftRefined
val myBarRefined: String get() = myBar.toString()
