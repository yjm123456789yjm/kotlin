/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import org.jetbrains.kotlin.analysis.api.components.KtReferenceResolveProvider
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade
import org.jetbrains.kotlin.analysis.api.descriptors.KtFe10AnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.components.base.Fe10KtAnalysisSessionComponent
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.references.fe10.base.KtFe10Reference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

internal class KtFe10ReferenceResolveProvider(
    override val analysisSession: KtFe10AnalysisSession
) : KtReferenceResolveProvider(), Fe10KtAnalysisSessionComponent {
    override fun resolveToSymbols(reference: KtReference): Collection<KtSymbol> {
        require(reference is KtFe10Reference)
        val bindingContext = analysisContext.analyze(reference.element, Fe10AnalysisFacade.AnalysisMode.PARTIAL)
        val targetDescriptors = if (reference is KtSimpleNameReference) {
            val expression = reference.expression
            if (expression is KtLabelReferenceExpression) {
                val target = bindingContext[BindingContext.LABEL_TARGET, expression]
                listOfNotNull(target?.let { bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, it] })
            } else if (expression is KtNameReferenceExpression && expression.getReferencedNameElementType() == KtTokens.THIS_KEYWORD) {
                listOfNotNull(expression.getResolvedCall(bindingContext)?.resultingDescriptor)
            } else {
                reference.getTargetDescriptors(bindingContext)
            }
        } else {
            reference.getTargetDescriptors(bindingContext)
        }
        return targetDescriptors.mapNotNull { descriptor ->
            descriptor.toKtSymbol(analysisContext)
        }
    }
}