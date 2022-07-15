/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.jvm.checkers.declaration

import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirInlineDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.isLocalMember
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.withSuppressedDiagnostics
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol

object FirJvmInlineDeclarationChecker : FirInlineDeclarationChecker() {
    override fun CheckerContext.check(declaration: FirFunction, reporter: DiagnosticReporter) {
        if (!declaration.isInline) {
            this.checkParametersInNotInline(declaration, reporter)
            return
        }
        // local inline functions are prohibited
        if (declaration.isLocalMember) {
            reporter.reportOn(declaration.source, FirErrors.NOT_YET_SUPPORTED_IN_INLINE, "Local inline functions")
            return
        }
        if (declaration !is FirPropertyAccessor && declaration !is FirSimpleFunction) return

        val effectiveVisibility = declaration.effectiveVisibility
        withSuppressedDiagnostics(declaration) {
            checkInlineFunctionBody(declaration, effectiveVisibility, reporter)
            checkCallableDeclaration(declaration, reporter)
        }
    }

    override val inlineVisitor get() = ::InlineVisitor

    class InlineVisitor(
        inlineFunction: FirFunction,
        inlineFunEffectiveVisibility: EffectiveVisibility,
        inalienableParameters: List<FirValueParameterSymbol>,
        session: FirSession,
        reporter: DiagnosticReporter
    ) : BasicInlineVisitor(
        inlineFunction,
        inlineFunEffectiveVisibility,
        inalienableParameters,
        session,
        reporter
    ) {
        override fun visitRegularClass(regularClass: FirRegularClass, data: CheckerContext) {
            if (!regularClass.classKind.isSingleton && data.containingDeclarations.lastOrNull() === inlineFunction) {
                with(data) { reporter.reportOn(regularClass.source, FirErrors.NOT_YET_SUPPORTED_IN_INLINE, "Local classes") }
            } else {
                super.visitRegularClass(regularClass, data)
            }
        }

        override fun visitSimpleFunction(simpleFunction: FirSimpleFunction, data: CheckerContext) {
            if (data.containingDeclarations.lastOrNull() === inlineFunction) {
                with(data) { reporter.reportOn(simpleFunction.source, FirErrors.NOT_YET_SUPPORTED_IN_INLINE, "Local functions") }
            } else {
                super.visitSimpleFunction(simpleFunction, data)
            }
        }
    }

    override fun CheckerContext.checkSuspendFunctionalParameterWithDefaultValue(
        param: FirValueParameter,
        reporter: DiagnosticReporter,
    ) {
        reporter.reportOn(
            param.source,
            FirErrors.NOT_YET_SUPPORTED_IN_INLINE,
            "Suspend functional parameters with default values"
        )
    }

    override fun CheckerContext.checkFunctionalParametersWithInheritedDefaultValues(
        function: FirSimpleFunction,
        reporter: DiagnosticReporter,
        overriddenSymbols: List<FirCallableSymbol<out FirCallableDeclaration>>,
    ) {
        val paramsWithDefaults = overriddenSymbols.flatMap {
            if (it !is FirFunctionSymbol<*>) return@flatMap emptyList<Int>()
            it.valueParameterSymbols.mapIndexedNotNull { idx, param ->
                idx.takeIf { param.hasDefaultValue }
            }
        }.toSet()
        function.valueParameters.forEachIndexed { idx, param ->
            if (param.defaultValue == null && paramsWithDefaults.contains(idx)) {
                reporter.reportOn(
                    param.source,
                    FirErrors.NOT_YET_SUPPORTED_IN_INLINE,
                    "Functional parameters with inherited default values"
                )
            }
        }
    }
}
