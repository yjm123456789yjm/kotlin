/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOnWithSuppression
import org.jetbrains.kotlin.fir.contracts.FirResolvedContractDescription
import org.jetbrains.kotlin.fir.declarations.FirContractDescriptionOwner
import org.jetbrains.kotlin.fir.declarations.FirFunction

object FirContractChecker : FirFunctionChecker() {
    // TODO: The message should vary. Migrate this to [ConeEffectExtractor] when creating fine-grained errors.
    private const val UNEXPECTED_CONSTRUCTION = "unexpected construction in contract description"

    override fun check(declaration: FirFunction, context: CheckerContext, reporter: DiagnosticReporter) {
        if (declaration !is FirContractDescriptionOwner) {
            return
        }
        val contractDescription = declaration.contractDescription as? FirResolvedContractDescription ?: return


        // Any statements that [ConeEffectExtractor] cannot extract effects will be in `unresolvedEffects`.
        for (statement in contractDescription.unresolvedEffects) {
            val source = statement.source
            if (source == null || source.kind is KtFakeSourceElementKind) continue

            // TODO: report on fine-grained locations, e.g., ... implies unresolved => report on unresolved, not the entire statement.
            //  but, sometimes, it's just reported on `contract`...
            reporter.reportOnWithSuppression(statement, FirErrors.ERROR_IN_CONTRACT_DESCRIPTION, UNEXPECTED_CONSTRUCTION, context)
        }
    }
}
