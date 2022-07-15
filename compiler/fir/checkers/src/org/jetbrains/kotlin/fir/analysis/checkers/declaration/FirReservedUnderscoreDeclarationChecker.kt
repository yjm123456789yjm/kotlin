/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.analysis.checkers.SourceNavigator
import org.jetbrains.kotlin.fir.analysis.checkers.checkUnderscoreDiagnostics
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.isUnderscore
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.diagnostics.withSuppressedDiagnostics
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef

object FirReservedUnderscoreDeclarationChecker : FirBasicDeclarationChecker() {
    override fun CheckerContext.check(declaration: FirDeclaration, reporter: DiagnosticReporter) {
        when (declaration) {
            is FirRegularClass, is FirTypeParameter, is FirProperty, is FirTypeAlias -> {
                this.reportIfUnderscore(declaration, reporter)
            }
            is FirFunction -> {
                if (declaration is FirSimpleFunction) {
                    this.reportIfUnderscore(declaration, reporter)
                }
                val isSingleUnderscoreAllowed = declaration is FirAnonymousFunction || declaration is FirPropertyAccessor
                for (parameter in declaration.valueParameters) {
                    withSuppressedDiagnostics(parameter) {
                        reportIfUnderscore(
                            parameter,
                            reporter,
                            isSingleUnderscoreAllowed = isSingleUnderscoreAllowed
                        )
                    }
                }
            }
            is FirFile -> {
                for (import in declaration.imports) {
                    checkUnderscoreDiagnostics(import.aliasSource, reporter, isExpression = false)
                }
            }
            else -> return
        }
    }

    private fun CheckerContext.reportIfUnderscore(
        declaration: FirDeclaration,
        reporter: DiagnosticReporter,
        isSingleUnderscoreAllowed: Boolean = false
    ) {
        val declarationSource = declaration.source
        if (declarationSource != null && declarationSource.kind !is KtFakeSourceElementKind) {
            with(SourceNavigator.forElement(declaration)) {
                val rawName = declaration.getRawName()
                if (rawName?.isUnderscore == true && !(isSingleUnderscoreAllowed && rawName == "_")) {
                    reporter.reportOn(
                        declarationSource,
                        FirErrors.UNDERSCORE_IS_RESERVED
                    )
                }
            }
        }

        val returnOrReceiverTypeRef = when (declaration) {
            is FirValueParameter -> declaration.returnTypeRef
            is FirFunction -> declaration.receiverTypeRef
            else -> null
        }

        if (returnOrReceiverTypeRef is FirResolvedTypeRef) {
            val delegatedTypeRef = returnOrReceiverTypeRef.delegatedTypeRef
            if (delegatedTypeRef is FirUserTypeRef) {
                for (qualifierPart in delegatedTypeRef.qualifier) {
                    checkUnderscoreDiagnostics(qualifierPart.source, reporter, isExpression = true)

                    for (typeArgument in qualifierPart.typeArgumentList.typeArguments) {
                        checkUnderscoreDiagnostics(typeArgument.source, reporter, isExpression = true)
                    }
                }
            }
        }
    }
}