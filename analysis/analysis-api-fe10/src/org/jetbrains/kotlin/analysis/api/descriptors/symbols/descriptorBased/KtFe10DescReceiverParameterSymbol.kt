/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased

import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.KtFe10DescSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.KtFe10NeverRestoringSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.KtReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor

class KtFe10DescReceiverParameterSymbol(
    override val descriptor: ReceiverParameterDescriptor,
    override val analysisContext: Fe10AnalysisContext
) : KtReceiverParameterSymbol(), KtFe10DescSymbol<ReceiverParameterDescriptor> {
    override val type: KtTypeAndAnnotations
        get() = withValidityAssertion {
            return descriptor.type.toKtTypeAndAnnotations(analysisContext)
        }

    override fun createPointer(): KtSymbolPointer<KtSymbol> {
        return KtPsiBasedSymbolPointer.createForSymbolFromSource(this) ?: KtFe10NeverRestoringSymbolPointer()
    }

}