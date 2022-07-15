/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.isRefinementUseless
import org.jetbrains.kotlin.fir.analysis.checkers.shouldCheckForExactType
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.*

// See .../types/CastDiagnosticsUtil.kt for counterparts, including isRefinementUseless, isExactTypeCast, isUpcast.
object FirUselessTypeOperationCallChecker : FirTypeOperatorCallChecker() {
    override fun CheckerContext.check(expression: FirTypeOperatorCall, reporter: DiagnosticReporter) {
        if (expression.operation !in FirOperation.TYPES) return
        val arg = expression.argument

        val candidateType = arg.typeRef.coneType.upperBoundIfFlexible().fullyExpandedType(session)
        if (candidateType is ConeErrorType) return

        val targetType = expression.conversionTypeRef.coneType.fullyExpandedType(session)
        if (targetType is ConeErrorType) return

        // x as? Type <=> x as Type?
        val refinedTargetType =
            if (expression.operation == FirOperation.SAFE_AS) {
                targetType.withNullability(ConeNullability.NULLABLE, session.typeContext)
            } else {
                targetType
            }
        if (isRefinementUseless(this, candidateType, refinedTargetType, shouldCheckForExactType(expression, this), arg)) {
            when (expression.operation) {
                FirOperation.IS -> reporter.reportOn(expression.source, FirErrors.USELESS_IS_CHECK, true)
                FirOperation.NOT_IS -> reporter.reportOn(expression.source, FirErrors.USELESS_IS_CHECK, false)
                FirOperation.AS, FirOperation.SAFE_AS -> {
                    if ((arg.typeRef as? FirResolvedTypeRef)?.isFromStubType != true) {
                        reporter.reportOn(expression.source, FirErrors.USELESS_CAST)
                    }
                }
                else -> throw AssertionError("Should not be here: ${expression.operation}")
            }
        }
    }
}
