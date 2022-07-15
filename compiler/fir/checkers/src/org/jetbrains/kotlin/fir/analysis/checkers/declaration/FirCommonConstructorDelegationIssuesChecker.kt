/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOnWithSuppression
import org.jetbrains.kotlin.fir.analysis.diagnostics.withSuppressedDiagnostics
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.references.FirErrorNamedReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.diagnostics.ConeAmbiguityError
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.ensureResolved
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object FirCommonConstructorDelegationIssuesChecker : FirRegularClassChecker() {
    override fun CheckerContext.check(declaration: FirRegularClass, reporter: DiagnosticReporter) {
        val cyclicConstructors = mutableSetOf<FirConstructor>()
        var hasPrimaryConstructor = false
        val isEffectivelyExpect = declaration.isEffectivelyExpect(containingDeclarations.lastOrNull() as? FirRegularClass, this)

        // candidates for further analysis
        val secondaryNonCyclicConstructors = mutableSetOf<FirConstructor>()

        for (it in declaration.declarations) {
            if (it is FirConstructor) {
                if (!it.isPrimary) {
                    secondaryNonCyclicConstructors += it

                    it.findCycle(cyclicConstructors)?.let { visited ->
                        cyclicConstructors += visited
                    }
                } else {
                    hasPrimaryConstructor = true
                }
            }
        }

        secondaryNonCyclicConstructors -= cyclicConstructors

        for (secondaryNonCyclicConstructor in secondaryNonCyclicConstructors) {
            withSuppressedDiagnostics(secondaryNonCyclicConstructor) {
                val delegatedConstructor = secondaryNonCyclicConstructor.delegatedConstructor
                if (hasPrimaryConstructor) {
                    if (!isEffectivelyExpect && delegatedConstructor?.isThis != true) {
                        if (delegatedConstructor?.source != null) {
                            reporter.reportOnWithSuppression(
                                delegatedConstructor,
                                FirErrors.PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED,
                                this
                            )
                        } else {
                            reporter.reportOn(
                                secondaryNonCyclicConstructor.source,
                                FirErrors.PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED
                            )
                        }
                    }
                } else {
                    val callee = delegatedConstructor?.calleeReference

                    // couldn't find proper super() constructor implicitly
                    if (callee is FirErrorNamedReference &&
                        callee.diagnostic is ConeAmbiguityError &&
                        delegatedConstructor.source?.kind is KtFakeSourceElementKind
                    ) {
                        reporter.reportOn(secondaryNonCyclicConstructor.source, FirErrors.EXPLICIT_DELEGATION_CALL_REQUIRED)
                    }
                }
            }
        }

        for (cyclicConstructor in cyclicConstructors) {
            this.withSuppressedDiagnostics(cyclicConstructor) {
                reporter.reportOn(cyclicConstructor.delegatedConstructor?.source, FirErrors.CYCLIC_CONSTRUCTOR_DELEGATION_CALL)
            }
        }
    }

    private fun FirConstructor.findCycle(knownCyclicConstructors: Set<FirConstructor> = emptySet()): Set<FirConstructor>? {
        val visitedConstructors = mutableSetOf(this)

        var it = this
        var delegated = this.getDelegated()

        while (!it.isPrimary && delegated != null) {
            if (delegated in visitedConstructors || delegated in knownCyclicConstructors) {
                return visitedConstructors
            }

            it = delegated
            delegated = delegated.getDelegated()
            visitedConstructors.add(it)
        }

        return null
    }

    private fun FirConstructor.getDelegated(): FirConstructor? {
        this.symbol.ensureResolved(FirResolvePhase.BODY_RESOLVE)
        val delegatedConstructorSymbol = delegatedConstructor
            ?.calleeReference.safeAs<FirResolvedNamedReference>()
            ?.resolvedSymbol
        @OptIn(SymbolInternals::class)
        return delegatedConstructorSymbol?.fir as? FirConstructor
    }
}
