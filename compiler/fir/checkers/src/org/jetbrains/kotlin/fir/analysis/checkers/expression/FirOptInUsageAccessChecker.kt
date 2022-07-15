/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.impl.FirNoReceiverExpression
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.coneType

object FirOptInUsageAccessChecker : FirQualifiedAccessChecker() {
    override fun CheckerContext.check(expression: FirQualifiedAccess, reporter: DiagnosticReporter) {
        val sourceKind = expression.source?.kind
        if (sourceKind is KtFakeSourceElementKind.DataClassGeneratedMembers ||
            sourceKind is KtFakeSourceElementKind.PropertyFromParameter
        ) return
        val resolvedSymbol = expression.calleeReference.resolvedSymbol ?: return
        val dispatchReceiverType =
            expression.dispatchReceiver.takeIf { it !is FirNoReceiverExpression }?.typeRef?.coneType?.fullyExpandedType(session)
        with(FirOptInUsageBaseChecker) {
            if (expression is FirVariableAssignment && resolvedSymbol is FirPropertySymbol) {
                val experimentalities = resolvedSymbol.loadExperimentalities(this@check, fromSetter = true, dispatchReceiverType) +
                        loadExperimentalitiesFromTypeArguments(this@check, expression.typeArguments)
                this@check.reportNotAcceptedExperimentalities(experimentalities, expression.lValue, reporter)
                return
            }
            val experimentalities = resolvedSymbol.loadExperimentalities(this@check, fromSetter = false, dispatchReceiverType) +
                    loadExperimentalitiesFromTypeArguments(this@check, expression.typeArguments)
            this@check.reportNotAcceptedExperimentalities(experimentalities, expression, reporter)
        }
    }
}