/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.light.classes.symbol.caches.SymbolLightClassFacadeCache

object Kapt4Main {


    fun run(configuration: CompilerConfiguration) {
        val analysisSession = buildStandaloneAnalysisAPISession {
            buildKtModuleProviderByCompilerConfiguration(configuration)
        }
        val project = analysisSession.project
        val lightClassFacadeCache = project.getService(SymbolLightClassFacadeCache::class.java)

//        lightClassFacadeCache.getOrCreateSymbolLightFacade()
        TODO()
    }
}
