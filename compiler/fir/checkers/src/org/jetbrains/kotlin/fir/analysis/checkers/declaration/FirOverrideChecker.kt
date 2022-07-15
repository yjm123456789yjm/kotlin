/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.findClosest
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirOptInUsageBaseChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirOptInUsageBaseChecker.Experimentality
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.withSuppressedDiagnostics
import org.jetbrains.kotlin.fir.analysis.overridesBackwardCompatibilityHelper
import org.jetbrains.kotlin.fir.containingClass
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.originalOrSelf
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.FirTypeScope
import org.jetbrains.kotlin.fir.scopes.getDirectOverriddenFunctions
import org.jetbrains.kotlin.fir.scopes.getDirectOverriddenProperties
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.ensureResolved
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visibilityChecker
import org.jetbrains.kotlin.types.AbstractTypeChecker
import org.jetbrains.kotlin.types.TypeCheckerState

object FirOverrideChecker : FirClassChecker() {
    override fun CheckerContext.check(declaration: FirClass, reporter: DiagnosticReporter) {
        val typeCheckerState = session.typeContext.newTypeCheckerState(
            errorTypesEqualToAnything = false,
            stubTypesEqualToAnything = false
        )

        val firTypeScope = declaration.unsubstitutedScope(this)

        for (callable in declaration.declarations) {
            if (callable !is FirCallableDeclaration) continue
            if (callable is FirSimpleFunction || callable is FirProperty) {
                withSuppressedDiagnostics(callable) {
                    this.checkMember(callable.symbol, declaration, reporter, typeCheckerState, firTypeScope)
                }
            }
        }
    }

    private fun FirTypeScope.retrieveDirectOverriddenOf(memberSymbol: FirCallableSymbol<*>): List<FirCallableSymbol<*>> {
        return when (memberSymbol) {
            is FirNamedFunctionSymbol -> {
                processFunctionsByName(memberSymbol.name) {}
                getDirectOverriddenFunctions(memberSymbol)
            }
            is FirPropertySymbol -> {
                processPropertiesByName(memberSymbol.name) {}
                getDirectOverriddenProperties(memberSymbol)
            }
            else -> throw IllegalArgumentException("unexpected member kind $memberSymbol")
        }
    }

    context(CheckerContext)
    private fun ConeKotlinType.substituteAllTypeParameters(
        overrideDeclaration: FirCallableSymbol<*>,
        baseDeclaration: FirCallableSymbol<*>
    ): ConeKotlinType {
        val overrideTypeParameters = overrideDeclaration.typeParameterSymbols
        if (overrideTypeParameters.isEmpty()) {
            return this
        }

        val baseTypeParameters = baseDeclaration.typeParameterSymbols

        val map = mutableMapOf<FirTypeParameterSymbol, ConeKotlinType>()
        val size = minOf(overrideTypeParameters.size, baseTypeParameters.size)

        for (it in 0 until size) {
            val to = overrideTypeParameters[it]
            val from = baseTypeParameters[it]

            map[from] = to.toConeType()
        }

        return substitutorByMap(map, session).substituteOrSelf(this)
    }

    private fun checkModality(
        overriddenSymbols: List<FirCallableSymbol<*>>,
    ): FirCallableSymbol<*>? {
        for (overridden in overriddenSymbols) {
            val modality = overridden.modality
            val isEffectivelyFinal = modality == null || modality == Modality.FINAL
            if (isEffectivelyFinal) {
                return overridden
            }
        }
        return null
    }

    private fun FirPropertySymbol.checkMutability(
        overriddenSymbols: List<FirCallableSymbol<*>>,
    ): FirCallableSymbol<*>? {
        if (isVar) return null
        return overriddenSymbols.find { (it as? FirPropertySymbol)?.isVar == true }
    }

    context(CheckerContext)
    private fun FirCallableSymbol<*>.checkVisibility(
        containingClass: FirClass,
        reporter: DiagnosticReporter,
        overriddenSymbols: List<FirCallableSymbol<*>>
    ) {
        if (overriddenSymbols.isEmpty()) return
        val visibilities = overriddenSymbols.map {
            it to it.visibility
        }.sortedBy { pair ->
            // Regard `null` compare as Int.MIN so that we can report CANNOT_CHANGE_... first deterministically
            Visibilities.compare(visibility, pair.second) ?: Int.MIN_VALUE
        }

        if (this is FirPropertySymbol) {
            getterSymbol?.checkVisibility(
                containingClass,
                reporter,
                overriddenSymbols.map { (it as FirPropertySymbol).getterSymbol ?: it }
            )
            setterSymbol?.checkVisibility(
                containingClass,
                reporter,
                overriddenSymbols.mapNotNull { (it as FirPropertySymbol).setterSymbol }
            )
        } else {
            for ((overridden, overriddenVisibility) in visibilities) {
                val compare = Visibilities.compare(visibility, overriddenVisibility)
                if (compare == null) {
                    reporter.reportCannotChangeAccessPrivilege(this, overridden)
                    break
                } else if (compare < 0) {
                    reporter.reportCannotWeakenAccessPrivilege(this, overridden)
                    break
                }
            }
        }

        if (this is FirPropertyAccessorSymbol) return
        val file = findClosest<FirFile>() ?: return
        val containingDeclarations = containingDeclarations + containingClass
        val visibilityChecker = session.visibilityChecker
        val hasVisibleBase = overriddenSymbols.any {
            it.ensureResolved(FirResolvePhase.STATUS)
            @OptIn(SymbolInternals::class)
            val fir = it.fir
            visibilityChecker.isVisible(
                fir,
                session,
                file,
                containingDeclarations,
                null,
                skipCheckForContainingClassVisibility = true
            )
        }
        if (!hasVisibleBase) {
            //NB: Old FE reports this in an attempt to override private member,
            //while the new FE doesn't treat super's private members as overridable, so you won't get them here
            //instead you will get NOTHING_TO_OVERRIDE, which seems acceptable
            reporter.reportOn(source, FirErrors.CANNOT_OVERRIDE_INVISIBLE_MEMBER, this, overriddenSymbols.first())
        }
    }

    context(CheckerContext)
    private fun FirCallableSymbol<*>.checkDeprecation(reporter: DiagnosticReporter, overriddenSymbols: List<FirCallableSymbol<*>>) {
        val ownDeprecation = this.deprecation
        if (ownDeprecation == null || ownDeprecation.isNotEmpty()) return
        for (overriddenSymbol in overriddenSymbols) {
            val deprecationInfoFromOverridden = overriddenSymbol.deprecation ?: continue
            val deprecationFromOverriddenSymbol = deprecationInfoFromOverridden.all
                ?: deprecationInfoFromOverridden.bySpecificSite?.values?.firstOrNull()
                ?: continue
            reporter.reportOn(source, FirErrors.OVERRIDE_DEPRECATION, overriddenSymbol, deprecationFromOverriddenSymbol)
            return
        }
    }

    // See [OverrideResolver#isReturnTypeOkForOverride]
    context(CheckerContext)
    private fun FirCallableSymbol<*>.checkReturnType(
        overriddenSymbols: List<FirCallableSymbol<*>>,
        typeCheckerState: TypeCheckerState,
    ): FirCallableSymbol<*>? {
        val overridingReturnType = resolvedReturnTypeRef.coneType

        // Don't report *_ON_OVERRIDE diagnostics according to an error return type. That should be reported separately.
        if (overridingReturnType is ConeErrorType) {
            return null
        }

        val bounds = overriddenSymbols.map { returnTypeCalculator.tryCalculateReturnType(it).coneType.upperBoundIfFlexible() }

        for (it in bounds.indices) {
            val overriddenDeclaration = overriddenSymbols[it]

            val overriddenReturnType = bounds[it].substituteAllTypeParameters(this, overriddenDeclaration)

            val isReturnTypeOkForOverride =
                if (overriddenDeclaration is FirPropertySymbol && overriddenDeclaration.isVar)
                    AbstractTypeChecker.equalTypes(typeCheckerState, overridingReturnType, overriddenReturnType)
                else
                    AbstractTypeChecker.isSubtypeOf(typeCheckerState, overridingReturnType, overriddenReturnType)

            if (!isReturnTypeOkForOverride) {
                return overriddenDeclaration
            }
        }

        return null
    }

    private fun CheckerContext.checkMember(
        member: FirCallableSymbol<*>,
        containingClass: FirClass,
        reporter: DiagnosticReporter,
        typeCheckerState: TypeCheckerState,
        firTypeScope: FirTypeScope
    ) {
        val overriddenMemberSymbols = firTypeScope.retrieveDirectOverriddenOf(member)

        if (!member.isOverride) {
            if (overriddenMemberSymbols.isEmpty() ||
                session.overridesBackwardCompatibilityHelper.overrideCanBeOmitted(overriddenMemberSymbols, this)
            ) {
                return
            }
            val kind = member.source?.kind
            // Only report if the current member has real source or it's a member property declared inside the primary constructor.

            if (kind is KtFakeSourceElementKind.DataClassGeneratedMembers) {
                overriddenMemberSymbols.find { it.isFinal }?.let { base ->
                    reporter.reportOn(
                        containingClass.source,
                        FirErrors.DATA_CLASS_OVERRIDE_CONFLICT,
                        member,
                        base
                    )
                }
                return
            }

            if (kind !is KtRealSourceElementKind && kind !is KtFakeSourceElementKind.PropertyFromParameter) return

            val overridden = overriddenMemberSymbols.first().originalOrSelf()
            val originalContainingClassSymbol = overridden.containingClass()?.toSymbol(session) as? FirRegularClassSymbol ?: return
            reporter.reportOn(
                member.source,
                FirErrors.VIRTUAL_MEMBER_HIDDEN,
                member,
                originalContainingClassSymbol
            )
            return
        }

        if (overriddenMemberSymbols.isEmpty()) {
            reporter.reportOn(member.source, FirErrors.NOTHING_TO_OVERRIDE, member)
            return
        }

        this.checkOverriddenExperimentalities(member, overriddenMemberSymbols, reporter)

        checkModality(overriddenMemberSymbols)?.let { callableSymbol ->
            reporter.reportOverridingFinalMember(member, callableSymbol)
        }

        if (member is FirPropertySymbol) {
            member.checkMutability(overriddenMemberSymbols)?.let { callableSymbol ->
                reporter.reportOn(member.source, FirErrors.VAR_OVERRIDDEN_BY_VAL, member, callableSymbol)
            }
        }

        member.checkVisibility(containingClass, reporter, overriddenMemberSymbols)

        member.checkDeprecation(reporter, overriddenMemberSymbols)

        val restriction = member.checkReturnType(
            overriddenSymbols = overriddenMemberSymbols,
            typeCheckerState = typeCheckerState
        ) ?: return
        when (member) {
            is FirNamedFunctionSymbol -> {
                reporter.reportOn(member.source, FirErrors.RETURN_TYPE_MISMATCH_ON_OVERRIDE, member, restriction)
            }
            is FirPropertySymbol -> {
                if (member.isVar) {
                    reporter.reportOn(member.source, FirErrors.VAR_TYPE_MISMATCH_ON_OVERRIDE, member, restriction)
                } else {
                    reporter.reportOn(member.source, FirErrors.PROPERTY_TYPE_MISMATCH_ON_OVERRIDE, member, restriction)
                }
            }
        }
    }

    @OptIn(SymbolInternals::class)
    private fun CheckerContext.checkOverriddenExperimentalities(
        memberSymbol: FirCallableSymbol<*>,
        overriddenMemberSymbols: List<FirCallableSymbol<*>>,
        reporter: DiagnosticReporter
    ) {
        with(FirOptInUsageBaseChecker) {
            val overriddenExperimentalities = mutableSetOf<Experimentality>()
            val session = session
            val overriddenSymbolsWithUnwrappedIntersectionOverrides = overriddenMemberSymbols.flatMap {
                when (it) {
                    is FirIntersectionOverridePropertySymbol -> it.intersections
                    is FirIntersectionOverrideFunctionSymbol -> it.intersections
                    else -> listOf(it)
                }
            }
            for (overriddenMemberSymbol in overriddenSymbolsWithUnwrappedIntersectionOverrides) {
                overriddenMemberSymbol.loadExperimentalitiesFromAnnotationTo(session, overriddenExperimentalities)
            }
            this@checkOverriddenExperimentalities.reportNotAcceptedOverrideExperimentalities(
                overriddenExperimentalities, memberSymbol, reporter
            )
        }
    }

    context(CheckerContext)
    private fun DiagnosticReporter.reportOverridingFinalMember(overriding: FirCallableSymbol<*>, overridden: FirCallableSymbol<*>) {
        overridden.containingClass()?.let { containingClass ->
            reportOn(overriding.source, FirErrors.OVERRIDING_FINAL_MEMBER, overridden, containingClass.name)
        }
    }

    context(CheckerContext)
    private fun DiagnosticReporter.reportCannotWeakenAccessPrivilege(
        overriding: FirCallableSymbol<*>,
        overridden: FirCallableSymbol<*>
    ) {
        val containingClass = overridden.containingClass() ?: return
        reportOn(
            overriding.source,
            FirErrors.CANNOT_WEAKEN_ACCESS_PRIVILEGE,
            overriding.visibility,
            overridden,
            containingClass.name
        )
    }

    context(CheckerContext)
    private fun DiagnosticReporter.reportCannotChangeAccessPrivilege(
        overriding: FirCallableSymbol<*>,
        overridden: FirCallableSymbol<*>
    ) {
        val containingClass = overridden.containingClass() ?: return
        reportOn(
            overriding.source,
            FirErrors.CANNOT_CHANGE_ACCESS_PRIVILEGE,
            overriding.visibility,
            overridden,
            containingClass.name
        )
    }

}
