/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer.hierarchical

import org.jetbrains.kotlin.commonizer.AbstractInlineSourcesCommonizationTest
import org.jetbrains.kotlin.commonizer.assertCommonized

class AliasedNumberTypeCommonizationTest : AbstractInlineSourcesCommonizationTest() {

    fun `test Int and Long - typealias`() {
        val result = commonize {
            outputTarget("(a, b)")
            simpleSingleSourceTarget("a", "typealias X = Int")
            simpleSingleSourceTarget("b", "typealias X = Long")
        }

        result.assertCommonized("(a, b)", "typealias X = Long")
    }

    fun `test UInt and ULong - typealias`() {
        val result = commonize {
            outputTarget("(a, b)")
            registerDependency("a", "b", "(a, b)") {
                source(
                    """
                    package kotlin
                    class UInt
                    class ULong
                """.trimIndent()
                )
            }
            simpleSingleSourceTarget("a", "typealias X = UInt")
            simpleSingleSourceTarget("b", "typealias X = ULong")
        }

        result.assertCommonized("(a, b)", "typealias X = ULong")
    }


    fun `test int and long - chain - typealias`() {
        val result = commonize {
            outputTarget("(a, b)")

            simpleSingleSourceTarget(
                "a", """
                    typealias A = Int
                    typealias B = A
                    typealias C = B
                    typealias X = C
                """.trimIndent()
            )

            simpleSingleSourceTarget(
                "b", """
                    typealias A = Long
                    typealias B = A
                    typealias X = B
                """.trimIndent()
            )
        }

        result.assertCommonized(
            "(a, b)", """
                typealias A = Long
                typealias B = A
                typealias X = Long
            """.trimIndent()
        )
    }
}