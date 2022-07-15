/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.findClosestClassOrObject
import org.jetbrains.kotlin.fir.declarations.fullyExpandedClass
import org.jetbrains.kotlin.fir.analysis.checkers.isSupertypeOf
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.references.FirSuperReference
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object FirQualifiedSupertypeExtendedByOtherSupertypeChecker : FirQualifiedAccessExpressionChecker() {
    override fun CheckerContext.check(expression: FirQualifiedAccessExpression, reporter: DiagnosticReporter) {
        if (languageVersionSettings.supportsFeature(LanguageFeature.QualifiedSupertypeMayBeExtendedByOtherSupertype)) return
        // require to be called over a super reference
        val superReference = expression.calleeReference.safeAs<FirSuperReference>()
            ?.takeIf { it.hadExplicitTypeInSource() }
            ?: return

        val explicitType = superReference.superTypeRef
            .toClassLikeSymbol(session)
            ?.fullyExpandedClass(session) as? FirClassSymbol<*>
            ?: return

        val surroundingType = findClosestClassOrObject()
            ?: return

        // how many supertypes of `surroundingType`
        // have `explicitType` as their supertype or
        // equal to it
        var count = 0
        var candidate: FirClassSymbol<*>? = null

        for (it in surroundingType.superTypeRefs) {
            val that = it.toClassLikeSymbol(session)
                ?.fullyExpandedClass(session) as? FirClassSymbol<*>
                ?: continue

            val isSupertype = explicitType.isSupertypeOf(that, session)

            if (explicitType == that || isSupertype) {
                if (isSupertype) {
                    candidate = that
                }

                count += 1

                if (count >= 2) {
                    break
                }
            }
        }

        if (count >= 2 && candidate != null) {
            reporter.reportOn(
                superReference.superTypeRef.source,
                FirErrors.QUALIFIED_SUPERTYPE_EXTENDED_BY_OTHER_SUPERTYPE,
                candidate
            )
        }
    }
}
