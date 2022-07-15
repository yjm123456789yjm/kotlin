/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.analysis.checkers.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.TypeCheckerProviderContext
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object FirTypeParameterBoundsChecker : FirTypeParameterChecker() {

    private val classKinds = setOf(
        ClassKind.CLASS,
        ClassKind.ENUM_CLASS,
        ClassKind.OBJECT
    )

    override fun CheckerContext.check(declaration: FirTypeParameter, reporter: DiagnosticReporter) {
        val containingDeclaration = containingDeclarations.lastOrNull() ?: return
        if (containingDeclaration is FirConstructor) return

        this.checkFinalUpperBounds(declaration, containingDeclaration, reporter)
        this.checkExtensionFunctionTypeBound(declaration, reporter)

        if (containingDeclaration.safeAs<FirMemberDeclaration>()?.isInlineOnly() != true) {
            this.checkOnlyOneTypeParameterBound(declaration, reporter)
        }

        this.checkBoundUniqueness(declaration, reporter)
        this.checkConflictingBounds(declaration, reporter)
        this.checkTypeAliasBound(declaration, containingDeclaration, reporter)
        this.checkDynamicBounds(declaration, reporter)
        checkInconsistentTypeParameterBounds(declaration, this, reporter)
    }

    private fun CheckerContext.checkFinalUpperBounds(
        declaration: FirTypeParameter,
        containingDeclaration: FirDeclaration,
        reporter: DiagnosticReporter
    ) {
        if (containingDeclaration is FirSimpleFunction && containingDeclaration.isOverride) return
        if (containingDeclaration is FirProperty && containingDeclaration.isOverride) return

        declaration.symbol.resolvedBounds.forEach { bound ->
            if (!bound.coneType.canHaveSubtypes(session)) {
                reporter.reportOn(bound.source, FirErrors.FINAL_UPPER_BOUND, bound.coneType)
            }
        }
    }

    private fun CheckerContext.checkExtensionFunctionTypeBound(declaration: FirTypeParameter, reporter: DiagnosticReporter) {
        declaration.symbol.resolvedBounds.forEach { bound ->
            if (bound.isExtensionFunctionType(session)) {
                reporter.reportOn(bound.source, FirErrors.UPPER_BOUND_IS_EXTENSION_FUNCTION_TYPE)
            }
        }
    }

    private fun CheckerContext.checkTypeAliasBound(
        declaration: FirTypeParameter,
        containingDeclaration: FirDeclaration,
        reporter: DiagnosticReporter
    ) {
        if (containingDeclaration is FirTypeAlias) {
            declaration.bounds.filter { it.source?.kind == KtRealSourceElementKind }.forEach { bound ->
                reporter.reportOn(bound.source, FirErrors.BOUND_ON_TYPE_ALIAS_PARAMETER_NOT_ALLOWED)
            }
        }
    }

    private fun CheckerContext.checkOnlyOneTypeParameterBound(declaration: FirTypeParameter, reporter: DiagnosticReporter) {
        val bounds = declaration.symbol.resolvedBounds.distinctBy { it.coneType }
        val (boundWithParam, otherBounds) = bounds.partition { it.coneType is ConeTypeParameterType }
        if (boundWithParam.size > 1 || (boundWithParam.size == 1 && otherBounds.isNotEmpty())) {
            // If there's only one problematic bound (either 2 type parameter bounds, or 1 type parameter bound + 1 other bound),
            // report the diagnostic on that bound

            //take TypeConstraint bounds only to report on the same point as old FE
            val constraintBounds = with(SourceNavigator.forElement(declaration)) {
                bounds.filter { it.isInTypeConstraint() }.toSet()
            }
            val reportOn =
                if (bounds.size == 2) {
                    val boundDecl = otherBounds.firstOrNull() ?: boundWithParam.last()
                    if (constraintBounds.contains(boundDecl)) boundDecl.source
                    else declaration.source
                } else {
                    declaration.source
                }
            reporter.reportOn(reportOn, FirErrors.BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER)
        }
    }

    private fun CheckerContext.checkBoundUniqueness(declaration: FirTypeParameter, reporter: DiagnosticReporter) {
        val seenClasses = mutableSetOf<FirRegularClassSymbol>()
        val allNonErrorBounds = declaration.symbol.resolvedBounds.filter { it !is FirErrorTypeRef }
        val uniqueBounds = allNonErrorBounds.distinctBy { it.coneType.classId ?: it.coneType }

        uniqueBounds.forEach { bound ->
            bound.coneType.toRegularClassSymbol(session)?.let { symbol ->
                if (classKinds.contains(symbol.classKind) && seenClasses.add(symbol) && seenClasses.size > 1) {
                    reporter.reportOn(bound.source, FirErrors.ONLY_ONE_CLASS_BOUND_ALLOWED)
                }
            }
        }

        allNonErrorBounds.minus(uniqueBounds).forEach { bound ->
            reporter.reportOn(bound.source, FirErrors.REPEATED_BOUND)
        }
    }

    private fun CheckerContext.checkConflictingBounds(declaration: FirTypeParameter, reporter: DiagnosticReporter) {
        if (declaration.bounds.size < 2) return

        fun anyConflictingTypes(types: List<ConeKotlinType>): Boolean {
            types.forEach { type ->
                if (!type.canHaveSubtypes(session)) {
                    types.forEach { otherType ->
                        if (type != otherType && !type.isRelated(session.typeContext, otherType)) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        if (anyConflictingTypes(declaration.symbol.resolvedBounds.map { it.coneType })) {
            reporter.reportOn(declaration.source, FirErrors.CONFLICTING_UPPER_BOUNDS, declaration.symbol)
        }
    }

    private fun CheckerContext.checkDynamicBounds(declaration: FirTypeParameter, reporter: DiagnosticReporter) {
        declaration.bounds.forEach { bound ->
            if (bound is FirDynamicTypeRef) {
                reporter.reportOn(bound.source, FirErrors.DYNAMIC_UPPER_BOUND)
            }
        }
    }

    private fun KotlinTypeMarker.isRelated(context: TypeCheckerProviderContext, type: KotlinTypeMarker?): Boolean =
        isSubtypeOf(context, type) || isSupertypeOf(context, type)

    private fun checkInconsistentTypeParameterBounds(
        declaration: FirTypeParameter,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        if (declaration.bounds.size <= 1) return

        val firTypeRefClasses = mutableListOf<Pair<FirTypeRef, FirRegularClassSymbol>>()
        val firRegularClassesSet = mutableSetOf<FirRegularClassSymbol>()

        for (bound in declaration.symbol.resolvedBounds) {
            val classSymbol = bound.toRegularClassSymbol(context.session)
            if (firRegularClassesSet.contains(classSymbol)) {
                // no need to throw INCONSISTENT_TYPE_PARAMETER_BOUNDS diagnostics here because REPEATED_BOUNDS diagnostic is already exist
                return
            }

            if (classSymbol != null) {
                firRegularClassesSet.add(classSymbol)
                firTypeRefClasses.add(Pair(bound, classSymbol))
            }
        }

        context.checkInconsistentTypeParameters(firTypeRefClasses, reporter, declaration.source, false)
    }
}
