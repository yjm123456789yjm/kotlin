/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOnWithSuppression
import org.jetbrains.kotlin.fir.declarations.FirBackingField
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.utils.hasExplicitBackingField
import org.jetbrains.kotlin.fir.declarations.utils.isLateInit
import org.jetbrains.kotlin.fir.types.*

object FirPropertyFieldTypeChecker : FirPropertyChecker() {
    override fun CheckerContext.check(declaration: FirProperty, reporter: DiagnosticReporter) {
        val backingField = declaration.backingField ?: return

        if (!declaration.hasExplicitBackingField) {
            return
        }

        val typeCheckerContext = session.typeContext.newTypeCheckerState(
            errorTypesEqualToAnything = false,
            stubTypesEqualToAnything = false
        )

        if (declaration.initializer != null) {
            reporter.reportOn(declaration.initializer?.source, FirErrors.PROPERTY_INITIALIZER_WITH_EXPLICIT_FIELD_DECLARATION)
        }

        if (backingField.isLateInit && declaration.isVal) {
            reporter.reportOnWithSuppression(backingField, FirErrors.LATEINIT_FIELD_IN_VAL_PROPERTY, this)
        }

        if (backingField.initializer == null && !backingField.isLateInit) {
            reporter.reportOnWithSuppression(backingField, FirErrors.PROPERTY_FIELD_DECLARATION_MISSING_INITIALIZER, this)
        } else if (backingField.initializer != null && backingField.isLateInit) {
            reporter.reportOnWithSuppression(backingField, FirErrors.LATEINIT_PROPERTY_FIELD_DECLARATION_WITH_INITIALIZER, this)
        }

        if (backingField.isLateInit && backingField.isNullable) {
            reporter.reportOnWithSuppression(backingField, FirErrors.LATEINIT_NULLABLE_BACKING_FIELD, this)
        }

        if (declaration.delegate != null) {
            reporter.reportOnWithSuppression(backingField, FirErrors.BACKING_FIELD_FOR_DELEGATED_PROPERTY, this)
        }

        if (backingField.returnTypeRef.coneType == declaration.returnTypeRef.coneType) {
            reporter.reportOnWithSuppression(backingField, FirErrors.REDUNDANT_EXPLICIT_BACKING_FIELD, this)
            return
        }

        if (!backingField.isSubtypeOf(declaration, typeCheckerContext)) {
            this.checkAsFieldNotSubtype(declaration, reporter)
        }

        if (!declaration.isSubtypeOf(backingField, typeCheckerContext)) {
            this.checkAsPropertyNotSubtype(declaration, reporter)
        }
    }

    private val FirBackingField.isNullable
        get() = when (val type = returnTypeRef.coneType) {
            is ConeTypeParameterType -> type.isNullable || type.lookupTag.typeParameterSymbol.resolvedBounds.any { it.coneType.isNullable }
            else -> type.isNullable
        }

    private val FirPropertyAccessor?.isNotExplicit
        get() = this == null || this is FirDefaultPropertyAccessor

    private fun CheckerContext.checkAsPropertyNotSubtype(
        property: FirProperty,
        reporter: DiagnosticReporter
    ) {
        if (property.isVar && property.setter.isNotExplicit) {
            reporter.reportOn(property.source, FirErrors.PROPERTY_MUST_HAVE_SETTER)
        }
    }

    private fun CheckerContext.checkAsFieldNotSubtype(
        property: FirProperty,
        reporter: DiagnosticReporter
    ) {
        if (property.getter.isNotExplicit) {
            reporter.reportOn(property.source, FirErrors.PROPERTY_MUST_HAVE_GETTER)
        }
    }
}
