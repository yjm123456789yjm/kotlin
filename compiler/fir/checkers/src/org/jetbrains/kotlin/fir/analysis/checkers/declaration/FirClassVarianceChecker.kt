/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.extractArgumentsTypeRefAndSource
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.withSuppressedDiagnostics
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.types.EnrichedProjectionKind
import org.jetbrains.kotlin.types.Variance

object FirClassVarianceChecker : FirClassChecker() {
    override fun CheckerContext.check(declaration: FirClass, reporter: DiagnosticReporter) {
        this.checkTypeParameters(declaration.typeParameters, Variance.OUT_VARIANCE, reporter)

        for (superTypeRef in declaration.superTypeRefs) {
            withSuppressedDiagnostics(superTypeRef) {
                checkVarianceConflict(superTypeRef, Variance.OUT_VARIANCE, reporter)
            }
        }

        for (member in declaration.declarations) {
            if (member is FirMemberDeclaration) {
                if (Visibilities.isPrivate(member.status.visibility)) {
                    continue
                }
            }

            if (member is FirTypeParameterRefsOwner && member !is FirClass) {
                this.checkTypeParameters(member.typeParameters, Variance.IN_VARIANCE, reporter)
            }

            if (member is FirCallableDeclaration) {
                withSuppressedDiagnostics(member) {
                    checkCallableDeclaration(member,  reporter)
                }
            }
        }
    }

    private fun CheckerContext.checkCallableDeclaration(
        member: FirCallableDeclaration,
        reporter: DiagnosticReporter
    ) {
        val memberSource = member.source
        if (member is FirSimpleFunction) {
            if (memberSource != null && memberSource.kind !is KtFakeSourceElementKind) {
                for (param in member.valueParameters) {
                    withSuppressedDiagnostics(param) {
                        this.checkVarianceConflict(param.returnTypeRef, Variance.IN_VARIANCE, reporter)
                    }
                }
            }
        }

        val returnTypeVariance =
            if (member is FirProperty && member.isVar) Variance.INVARIANT else Variance.OUT_VARIANCE

        var returnSource = member.returnTypeRef.source
        if (returnSource != null && memberSource != null) {
            if (returnSource.kind is KtFakeSourceElementKind && memberSource.kind !is KtFakeSourceElementKind) {
                returnSource = memberSource
            }
        }

        withSuppressedDiagnostics(member.returnTypeRef) {
            this.checkVarianceConflict(member.returnTypeRef, returnTypeVariance, reporter, returnSource)
        }

        val receiverTypeRef = member.receiverTypeRef
        if (receiverTypeRef != null) {
            withSuppressedDiagnostics(receiverTypeRef) {
                this.checkVarianceConflict(receiverTypeRef, Variance.IN_VARIANCE, reporter)
            }
        }
    }

    private fun CheckerContext.checkTypeParameters(
        typeParameters: List<FirTypeParameterRef>,
        variance: Variance,
        reporter: DiagnosticReporter
    ) {
        for (typeParameter in typeParameters) {
            if (typeParameter is FirTypeParameter) {
                withSuppressedDiagnostics(typeParameter) {
                    for (bound in typeParameter.symbol.resolvedBounds) {
                        withSuppressedDiagnostics(bound) {
                            checkVarianceConflict(bound, variance, reporter)
                        }
                    }
                }
            }
        }
    }

    private fun CheckerContext.checkVarianceConflict(
        type: FirTypeRef,
        variance: Variance,
        reporter: DiagnosticReporter,
        source: KtSourceElement? = null
    ) {
        checkVarianceConflict(type.coneType, variance, type, type.coneType, reporter, source)
    }

    private fun CheckerContext.checkVarianceConflict(
        type: ConeKotlinType,
        variance: Variance,
        typeRef: FirTypeRef?,
        containingType: ConeKotlinType,
        reporter: DiagnosticReporter,
        source: KtSourceElement? = null,
        isInAbbreviation: Boolean = false
    ) {
        if (type is ConeTypeParameterType) {
            val fullyExpandedType = type.fullyExpandedType(session)
            val typeParameterSymbol = type.lookupTag.typeParameterSymbol
            val resultSource = source ?: typeRef?.source
            if (resultSource != null &&
                !typeParameterSymbol.variance.allowsPosition(variance) &&
                !fullyExpandedType.attributes.contains(CompilerConeAttributes.UnsafeVariance)
            ) {
                val factory =
                    if (isInAbbreviation) FirErrors.TYPE_VARIANCE_CONFLICT_IN_EXPANDED_TYPE else FirErrors.TYPE_VARIANCE_CONFLICT_ERROR
                reporter.reportOn(
                    resultSource,
                    factory,
                    typeParameterSymbol,
                    typeParameterSymbol.variance,
                    variance,
                    containingType
                )
            }
            return
        }

        if (type is ConeClassLikeType) {
            val fullyExpandedType = type.fullyExpandedType(session)
            val classSymbol = fullyExpandedType.lookupTag.toSymbol(session)
            if (classSymbol is FirClassSymbol<*>) {
                val typeRefAndSourcesForArguments = extractArgumentsTypeRefAndSource(typeRef)
                for ((index, typeArgument) in fullyExpandedType.typeArguments.withIndex()) {
                    val paramVariance = classSymbol.typeParameterSymbols.getOrNull(index)?.variance ?: continue

                    val argVariance = when (typeArgument.kind) {
                        ProjectionKind.IN -> Variance.IN_VARIANCE
                        ProjectionKind.OUT -> Variance.OUT_VARIANCE
                        ProjectionKind.INVARIANT -> Variance.INVARIANT
                        else -> continue
                    }

                    val typeArgumentType = typeArgument.type ?: continue

                    val newVariance = when (EnrichedProjectionKind.getEffectiveProjectionKind(paramVariance, argVariance)) {
                        EnrichedProjectionKind.OUT -> variance
                        EnrichedProjectionKind.IN -> variance.opposite()
                        EnrichedProjectionKind.INV -> Variance.INVARIANT
                        EnrichedProjectionKind.STAR -> null // CONFLICTING_PROJECTION error was reported
                    }

                    if (newVariance != null) {
                        val subTypeRefAndSource = typeRefAndSourcesForArguments?.getOrNull(index)

                        checkVarianceConflict(
                            typeArgumentType, newVariance, subTypeRefAndSource?.typeRef, containingType,
                            reporter, subTypeRefAndSource?.typeRef?.source ?: source,
                            fullyExpandedType != type
                        )
                    }
                }
            }
        }
    }
}
