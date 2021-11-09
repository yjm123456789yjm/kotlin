/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.deprecation.DeprecationResolver
import org.jetbrains.kotlin.resolve.lazy.ResolveSession

interface Fe10AnalysisFacade {
    companion object {
        fun getInstance(project: Project): Fe10AnalysisFacade {
            return ServiceManager.getService(project, Fe10AnalysisFacade::class.java)
        }
    }

    fun getAnalysisServices(element: KtElement): Fe10AnalysisServices

    fun analyze(element: KtElement, mode: AnalysisMode = AnalysisMode.FULL): BindingContext

    fun getOrigin(file: VirtualFile): KtSymbolOrigin

    enum class AnalysisMode {
        FULL,
        PARTIAL_WITH_DIAGNOSTICS,
        PARTIAL
    }
}

interface Fe10AnalysisServices {
    val resolveSession: ResolveSession
    val deprecationResolver: DeprecationResolver
}

class Fe10AnalysisContext(
    facade: Fe10AnalysisFacade,
    services: Fe10AnalysisServices,
    val token: ValidityToken
) : Fe10AnalysisFacade by facade, Fe10AnalysisServices by services {
    companion object {
        fun create(contextElement: KtElement, token: ValidityToken): Fe10AnalysisContext {
            val facade = Fe10AnalysisFacade.getInstance(contextElement.project)
            val services = facade.getAnalysisServices(contextElement)
            return Fe10AnalysisContext(facade, services, token)
        }
    }

    val builtIns: KotlinBuiltIns
        get() = resolveSession.moduleDescriptor.builtIns
}