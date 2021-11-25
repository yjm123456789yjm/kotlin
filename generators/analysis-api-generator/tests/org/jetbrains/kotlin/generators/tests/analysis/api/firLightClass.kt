/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.tests.analysis.api

import org.jetbrains.kotlin.generators.TestGroupSuite
import org.jetbrains.kotlin.light.classes.symbol.AbstractFirLightClassForSourceTest

internal fun TestGroupSuite.generateFirLightClassTests() {
    testGroup(
        testsRoot = "analysis/symbol-light-classes/tests",
        testDataRoot = "analysis/symbol-light-classes/testData"
    ) {
        testClass<AbstractFirLightClassForSourceTest> {
            model("source")
        }
    }
}
