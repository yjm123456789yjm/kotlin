/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClassSymbol
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.getDeprecation
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirDelegatedConstructorCall
import org.jetbrains.kotlin.fir.expressions.FirResolvable
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.resolved
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.resolve.deprecation.DeprecationInfo
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object FirDeprecationChecker : FirBasicExpressionChecker() {

    private val allowedSourceKinds = setOf(
        KtRealSourceElementKind,
        KtFakeSourceElementKind.DesugaredIncrementOrDecrement
    )

    override fun CheckerContext.check(expression: FirStatement, reporter: DiagnosticReporter) {
        if (!allowedSourceKinds.contains(expression.source?.kind)) return
        if (expression is FirAnnotation || expression is FirDelegatedConstructorCall) return //checked by FirDeprecatedTypeChecker
        val resolvable = expression as? FirResolvable ?: return
        val reference = resolvable.calleeReference.resolved ?: return
        val referencedSymbol = reference.resolvedSymbol

        this.reportDeprecationIfNeeded(reference.source, referencedSymbol, expression, reporter)
    }

    internal fun CheckerContext.reportDeprecationIfNeeded(
        source: KtSourceElement?,
        referencedSymbol: FirBasedSymbol<*>,
        callSite: FirElement?,
        reporter: DiagnosticReporter
    ) {
        val deprecation = getWorstDeprecation(callSite, referencedSymbol, this) ?: return
        this.reportDeprecation(source, referencedSymbol, deprecation, reporter)
    }

    internal fun CheckerContext.reportDeprecation(
        source: KtSourceElement?,
        referencedSymbol: FirBasedSymbol<*>,
        deprecationInfo: DeprecationInfo,
        reporter: DiagnosticReporter
    ) {
        val diagnostic = when (deprecationInfo.deprecationLevel) {
            DeprecationLevelValue.ERROR, DeprecationLevelValue.HIDDEN -> FirErrors.DEPRECATION_ERROR
            DeprecationLevelValue.WARNING -> FirErrors.DEPRECATION
        }
        reporter.reportOn(source, diagnostic, referencedSymbol, deprecationInfo.message ?: "")
    }

    private fun getWorstDeprecation(
        callSite: FirElement?,
        symbol: FirBasedSymbol<*>,
        context: CheckerContext
    ): DeprecationInfo? {
        val deprecationInfos = listOfNotNull(
            symbol.getDeprecation(callSite),
            symbol.safeAs<FirConstructorSymbol>()
                ?.resolvedReturnTypeRef
                ?.toRegularClassSymbol(context.session)
                ?.getDeprecation(callSite)
        )
        return deprecationInfos.maxOrNull()
    }
}
