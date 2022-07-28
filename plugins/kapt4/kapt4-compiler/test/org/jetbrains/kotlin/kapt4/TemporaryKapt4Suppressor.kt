/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import org.jetbrains.kotlin.kapt4.Kapt4Directives.FIR_ALMOST_DONE
import org.jetbrains.kotlin.test.WrappedException
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions
import org.jetbrains.kotlin.test.services.moduleStructure

class TemporaryKapt4Suppressor(testServices: TestServices) : AfterAnalysisChecker(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(Kapt4Directives)

    override fun suppressIfNeeded(failedAssertions: List<WrappedException>): List<WrappedException> {
        if (!testServices.moduleStructure.modules.any { FIR_ALMOST_DONE in it.directives }) {
            return failedAssertions
        }
        if (failedAssertions.isEmpty()) {
            testServices.assertions.fail { "Test passes, remove $FIR_ALMOST_DONE directive" }
        }
        return emptyList()
    }
}

object Kapt4Directives : SimpleDirectivesContainer() {
    val FIR_ALMOST_DONE by directive("This tests is almost passes with KAPT4")
}
