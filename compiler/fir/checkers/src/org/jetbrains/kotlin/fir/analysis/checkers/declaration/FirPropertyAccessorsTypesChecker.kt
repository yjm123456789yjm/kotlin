/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.findClosestClassOrObject
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.diagnostics.withSuppressedDiagnostics
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.canHaveAbstractDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isOpen
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isUnit

object FirPropertyAccessorsTypesChecker : FirPropertyChecker() {
    override fun CheckerContext.check(declaration: FirProperty, reporter: DiagnosticReporter) {
        checkGetter(declaration, reporter)
        checkSetter(declaration, reporter)
    }

    private fun CheckerContext.checkGetter(property: FirProperty, reporter: DiagnosticReporter) {
        val getter = property.getter ?: return
        val propertyType = property.returnTypeRef.coneType

        withSuppressedDiagnostics(getter) {
            checkAccessorForDelegatedProperty(property, getter, reporter)
            if (getter.visibility != property.visibility) {
                reporter.reportOn(getter.source, FirErrors.GETTER_VISIBILITY_DIFFERS_FROM_PROPERTY_VISIBILITY)
            }
            if (property.symbol.callableId.classId != null && getter.body != null && property.delegate == null) {
                if (isLegallyAbstract(property, this)) {
                    reporter.reportOn(getter.source, FirErrors.ABSTRACT_PROPERTY_WITH_GETTER)
                }
            }
            val getterReturnTypeRef = getter.returnTypeRef
            if (getterReturnTypeRef.source?.kind is KtFakeSourceElementKind) {
                return
            }
            val getterReturnType = getterReturnTypeRef.coneType
            if (propertyType is ConeErrorType || getterReturnType is ConeErrorType) {
                return
            }
            if (getterReturnType != property.returnTypeRef.coneType) {
                val getterReturnTypeSource = getterReturnTypeRef.source
                withSuppressedDiagnostics(getterReturnTypeRef) {
                    reporter.reportOn(getterReturnTypeSource, FirErrors.WRONG_GETTER_RETURN_TYPE, propertyType, getterReturnType)
                }
            }
        }
    }

    private fun CheckerContext.checkSetter(property: FirProperty, reporter: DiagnosticReporter) {
        val setter = property.setter ?: return
        val propertyType = property.returnTypeRef.coneType

        withSuppressedDiagnostics(setter) {
            if (property.isVal) {
                reporter.reportOn(setter.source, FirErrors.VAL_WITH_SETTER)
            }
            checkAccessorForDelegatedProperty(property, setter, reporter)
            val visibilityCompareResult = setter.visibility.compareTo(property.visibility)
            if (visibilityCompareResult == null || visibilityCompareResult > 0) {
                reporter.reportOn(setter.source, FirErrors.SETTER_VISIBILITY_INCONSISTENT_WITH_PROPERTY_VISIBILITY)
            }
            if (property.symbol.callableId.classId != null && property.delegate == null) {
                val isLegallyAbstract = isLegallyAbstract(property, this)
                if (setter.visibility == Visibilities.Private && property.visibility != Visibilities.Private) {
                    if (isLegallyAbstract) {
                        reporter.reportOn(setter.source, FirErrors.PRIVATE_SETTER_FOR_ABSTRACT_PROPERTY)
                    } else if (property.isOpen) {
                        reporter.reportOn(setter.source, FirErrors.PRIVATE_SETTER_FOR_OPEN_PROPERTY)
                    }
                }
                if (isLegallyAbstract && setter.body != null) {
                    reporter.reportOn(setter.source, FirErrors.ABSTRACT_PROPERTY_WITH_SETTER)
                }
            }

            val valueSetterParameter = setter.valueParameters.first()
            if (valueSetterParameter.isVararg) {
                return
            }
            val valueSetterType = valueSetterParameter.returnTypeRef.coneType
            val valueSetterTypeSource = valueSetterParameter.returnTypeRef.source
            if (propertyType is ConeErrorType || valueSetterType is ConeErrorType) {
                return
            }

            if (valueSetterType != propertyType) {
                withSuppressedDiagnostics(valueSetterParameter) {
                    reporter.reportOn(valueSetterTypeSource, FirErrors.WRONG_SETTER_PARAMETER_TYPE, propertyType, valueSetterType)
                }
            }

            val setterReturnType = setter.returnTypeRef.coneType

            if (!setterReturnType.isUnit) {
                withSuppressedDiagnostics(setter.returnTypeRef) {
                    reporter.reportOn(setter.returnTypeRef.source, FirErrors.WRONG_SETTER_RETURN_TYPE)
                }
            }
        }
    }

    private fun CheckerContext.checkAccessorForDelegatedProperty(
        property: FirProperty,
        accessor: FirPropertyAccessor,
        reporter: DiagnosticReporter
    ) {
        if (property.delegateFieldSymbol != null && accessor.body != null &&
            accessor.source?.kind != KtFakeSourceElementKind.DelegatedPropertyAccessor
        ) {
            reporter.reportOn(accessor.source, FirErrors.ACCESSOR_FOR_DELEGATED_PROPERTY)
        }
    }

    private fun isLegallyAbstract(property: FirProperty, context: CheckerContext): Boolean {
        return property.isAbstract && context.findClosestClassOrObject().let { it is FirRegularClass && it.canHaveAbstractDeclaration }
    }
}
