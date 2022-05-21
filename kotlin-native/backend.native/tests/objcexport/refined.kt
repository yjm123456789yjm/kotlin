/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package refined

@ExperimentalObjCRefinement
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@RefinesForObjC
annotation class MyRefinedForObjC

@ExperimentalObjCRefinement
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@RefinesInSwift
annotation class MyRefinedInSwift

@OptIn(ExperimentalObjCRefinement::class)
@RefinedForObjC
fun foo(): Int = 1

@OptIn(ExperimentalObjCRefinement::class)
@RefinedInSwift
fun fooRefined(): String = foo().toString()

@OptIn(ExperimentalObjCRefinement::class)
@MyRefinedForObjC
fun myFoo(): Int = 2

@OptIn(ExperimentalObjCRefinement::class)
@MyRefinedInSwift
fun myFooRefined(): String = myFoo().toString()

@OptIn(ExperimentalObjCRefinement::class)
@RefinedForObjC
val bar: Int = 3

@OptIn(ExperimentalObjCRefinement::class)
@RefinedInSwift
val barRefined: String get() = bar.toString()

@OptIn(ExperimentalObjCRefinement::class)
@MyRefinedForObjC
val myBar: Int = 4

@OptIn(ExperimentalObjCRefinement::class)
@MyRefinedInSwift
val myBarRefined: String get() = myBar.toString()
