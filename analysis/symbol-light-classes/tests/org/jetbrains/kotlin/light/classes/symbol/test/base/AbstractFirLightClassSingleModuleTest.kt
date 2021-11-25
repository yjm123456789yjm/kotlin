/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol.test.base

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyse
import org.jetbrains.kotlin.analysis.api.analyseInDependedAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.FirFrontendApiTestConfiguratorService
import org.jetbrains.kotlin.analysis.api.impl.barebone.test.AbstractFrontendApiTest
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractFirLightClassSingleModuleTest : AbstractFrontendApiTest(FirFrontendApiTestConfiguratorService) {
    protected fun <R> analyseForTest(contextElement: KtElement, action: KtAnalysisSession.() -> R): R {
        return if (useDependedAnalysisSession) {
            // Depended mode does not support analysing a KtFile.
            // See org.jetbrains.kotlin.analysis.low.level.api.fir.api.LowLevelFirApiFacadeForResolveOnAir#getResolveStateForDependentCopy
            if (contextElement is KtFile) {
                throw SkipDependedModeException()
            }

            require(!contextElement.isPhysical)
            analyseInDependedAnalysisSession(configurator.getOriginalFile(contextElement.containingKtFile), contextElement, action)
        } else {
            analyse(contextElement, action)
        }
    }
}
