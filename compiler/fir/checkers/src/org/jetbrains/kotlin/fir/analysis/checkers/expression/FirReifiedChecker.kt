/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.chooseFactory
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.*
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.StandardClassIds

object FirReifiedChecker : FirQualifiedAccessExpressionChecker() {
    override fun CheckerContext.check(expression: FirQualifiedAccessExpression, reporter: DiagnosticReporter) {
        val calleReference = expression.calleeReference
        val typeArguments = expression.typeArguments
        val typeParameters = calleReference.toResolvedCallableSymbol()?.typeParameterSymbols ?: return

        val count = minOf(typeArguments.size, typeParameters.size)
        for (index in 0 until count) {
            val typeArgumentProjection = typeArguments.elementAt(index)
            val source = typeArgumentProjection.source ?: calleReference.source
            val typeArgument = typeArgumentProjection.toConeTypeProjection().type
            val typeParameter = typeParameters[index]

            if (source != null && typeParameter.isReifiedTypeParameterOrFromKotlinArray()) {
                checkArgumentAndReport(typeArgument, source, false, reporter)
            }
        }
    }

    private fun FirTypeParameterSymbol.isReifiedTypeParameterOrFromKotlinArray(): Boolean {
        val containingDeclaration = containingDeclarationSymbol
        return isReified ||
                containingDeclaration is FirRegularClassSymbol && containingDeclaration.classId == StandardClassIds.Array
    }

    private fun CheckerContext.checkArgumentAndReport(
        typeArgument: ConeKotlinType?,
        source: KtSourceElement,
        isArray: Boolean,
        reporter: DiagnosticReporter
    ) {
        if (typeArgument?.classId == StandardClassIds.Array) {
            checkArgumentAndReport(
                typeArgument.typeArguments[0].type,
                source,
                true,
                reporter
            )
            return
        }

        if (typeArgument is ConeTypeParameterType) {
            val factory = if (isArray) {
                FirErrors.TYPE_PARAMETER_AS_REIFIED_ARRAY.chooseFactory(this@checkArgumentAndReport)
            } else {
                FirErrors.TYPE_PARAMETER_AS_REIFIED
            }
            val symbol = typeArgument.lookupTag.typeParameterSymbol
            if (!symbol.isReified) {
                reporter.reportOn(source, factory, symbol)
            }
        } else if (typeArgument != null && typeArgument.cannotBeReified()) {
            reporter.reportOn(source, FirErrors.REIFIED_TYPE_FORBIDDEN_SUBSTITUTION, typeArgument)
            return
        }
    }

    private fun ConeKotlinType.cannotBeReified(): Boolean {
        return this.isNothing || this.isNullableNothing || this is ConeCapturedType
    }
}
