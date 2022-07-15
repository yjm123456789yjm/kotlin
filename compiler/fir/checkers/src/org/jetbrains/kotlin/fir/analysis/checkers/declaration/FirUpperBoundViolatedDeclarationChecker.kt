/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fir.analysis.checkers.checkUpperBoundViolated
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.withSuppressedDiagnostics
import org.jetbrains.kotlin.fir.declarations.*

object FirUpperBoundViolatedDeclarationChecker : FirBasicDeclarationChecker() {
    override fun CheckerContext.check(declaration: FirDeclaration, reporter: DiagnosticReporter) {
        if (declaration is FirClass) {
            for (typeParameter in declaration.typeParameters) {
                if (typeParameter is FirTypeParameter) {
                    withSuppressedDiagnostics(typeParameter) {
                        for (bound in typeParameter.bounds) {
                            checkUpperBoundViolated(bound, reporter)
                        }
                    }
                }
            }

            for (superTypeRef in declaration.superTypeRefs) {
                this.checkUpperBoundViolated(superTypeRef, reporter)
            }
        } else if (declaration is FirTypeAlias) {
            this.checkUpperBoundViolated(declaration.expandedTypeRef, reporter, isIgnoreTypeParameters = true)
        } else if (declaration is FirCallableDeclaration) {
            if (declaration.returnTypeRef.source?.kind !is KtFakeSourceElementKind) {
                this.checkUpperBoundViolated(
                    declaration.returnTypeRef, reporter, isIgnoreTypeParameters = containingDeclarations.lastOrNull() is FirTypeAlias
                )
            }
        }
    }
}
