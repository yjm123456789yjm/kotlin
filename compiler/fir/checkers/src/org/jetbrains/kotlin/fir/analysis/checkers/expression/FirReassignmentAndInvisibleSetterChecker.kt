/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.findClosest
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClassSymbol
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirExpressionWithSmartcast
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.originalForSubstitutionOverride
import org.jetbrains.kotlin.fir.references.FirBackingFieldReference
import org.jetbrains.kotlin.fir.resolve.calls.ExpressionReceiverValue
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.visibilityChecker

object FirReassignmentAndInvisibleSetterChecker : FirVariableAssignmentChecker() {
    override fun CheckerContext.check(expression: FirVariableAssignment, reporter: DiagnosticReporter) {
        checkInvisibleSetter(expression, reporter)
        checkValReassignmentViaBackingField(expression, reporter)
        checkValReassignmentOnValueParameter(expression, reporter)
    }

    private fun CheckerContext.checkInvisibleSetter(
        expression: FirVariableAssignment,
        reporter: DiagnosticReporter
    ) {
        fun shouldInvisibleSetterBeReported(symbol: FirPropertySymbol): Boolean {
            @OptIn(SymbolInternals::class)
            val setterFir = symbol.setterSymbol?.fir ?: symbol.originalForSubstitutionOverride?.setterSymbol?.fir
            if (setterFir != null) {
                return !session.visibilityChecker.isVisible(
                    setterFir,
                    session,
                    findClosest()!!,
                    containingDeclarations,
                    ExpressionReceiverValue(expression.dispatchReceiver),
                )
            }

            return false
        }

        val callableSymbol = expression.calleeReference.toResolvedCallableSymbol()
        if (callableSymbol is FirPropertySymbol && shouldInvisibleSetterBeReported(callableSymbol)) {
            val explicitReceiver = expression.explicitReceiver
            // Try to get type from smartcast
            if (explicitReceiver is FirExpressionWithSmartcast) {
                val symbol = explicitReceiver.originalType.toRegularClassSymbol(session)
                if (symbol != null) {
                    for (declarationSymbol in symbol.declarationSymbols) {
                        if (declarationSymbol is FirPropertySymbol && declarationSymbol.name == callableSymbol.name) {
                            if (!shouldInvisibleSetterBeReported(declarationSymbol)) {
                                return
                            }
                        }
                    }
                }
            }
            reporter.reportOn(
                expression.source,
                FirErrors.INVISIBLE_SETTER,
                callableSymbol,
                callableSymbol.setterSymbol?.visibility ?: Visibilities.Private,
                callableSymbol.callableId
            )
        }
    }

    private fun CheckerContext.checkValReassignmentViaBackingField(
        expression: FirVariableAssignment,
        reporter: DiagnosticReporter
    ) {
        val backingFieldReference = expression.lValue as? FirBackingFieldReference ?: return
        val propertySymbol = backingFieldReference.resolvedSymbol
        if (propertySymbol.isVar) return
        val closestGetter = findClosest<FirPropertyAccessor> { it.isGetter }?.symbol ?: return
        if (propertySymbol.getterSymbol != closestGetter) return

        reporter.reportOn(backingFieldReference.source, FirErrors.VAL_REASSIGNMENT_VIA_BACKING_FIELD, propertySymbol)
    }

    private fun CheckerContext.checkValReassignmentOnValueParameter(
        expression: FirVariableAssignment,
        reporter: DiagnosticReporter
    ) {
        val valueParameter = expression.lValue.resolvedSymbol as? FirValueParameterSymbol ?: return
        reporter.reportOn(expression.lValue.source, FirErrors.VAL_REASSIGNMENT, valueParameter)
    }
}
