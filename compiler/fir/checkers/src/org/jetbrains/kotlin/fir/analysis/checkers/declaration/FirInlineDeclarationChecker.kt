/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.builtins.StandardNames.BACKING_FIELD
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.isInlineOnly
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.analysis.collectors.AbstractDiagnosticCollectorVisitor
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.getModifier
import org.jetbrains.kotlin.fir.analysis.diagnostics.withSuppressedDiagnostics
import org.jetbrains.kotlin.fir.containingClass
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.references.FirSuperReference
import org.jetbrains.kotlin.fir.types.isBuiltinFunctionalType
import org.jetbrains.kotlin.fir.types.isFunctionalType
import org.jetbrains.kotlin.fir.types.isSuspendFunctionType
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.resolve.transformers.publishedApiEffectiveVisibility
import org.jetbrains.kotlin.fir.scopes.getDirectOverriddenMembers
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.isNullable
import org.jetbrains.kotlin.fir.types.toSymbol
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitor
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.util.OperatorNameConventions

abstract class FirInlineDeclarationChecker : FirFunctionChecker() {
    override fun CheckerContext.check(declaration: FirFunction, reporter: DiagnosticReporter) {
        if (!declaration.isInline) {
            this.checkParametersInNotInline(declaration, reporter)
            return
        }

        if (declaration !is FirPropertyAccessor && declaration !is FirSimpleFunction) return

        val effectiveVisibility = declaration.effectiveVisibility
        withSuppressedDiagnostics(declaration) {
            checkInlineFunctionBody(declaration, effectiveVisibility, reporter)
            checkCallableDeclaration(declaration, reporter)
        }
    }

    protected fun CheckerContext.checkInlineFunctionBody(
        function: FirFunction,
        effectiveVisibility: EffectiveVisibility,
        reporter: DiagnosticReporter
    ) {
        val body = function.body ?: return
        val inalienableParameters = function.valueParameters.filter {
            if (it.isNoinline) return@filter false
            val type = it.returnTypeRef.coneType
            !type.isMarkedNullable && type.isFunctionalType(session) { kind -> !kind.isReflectType }
        }.map { it.symbol }

        val visitor = this@FirInlineDeclarationChecker.inlineVisitor(
            function,
            effectiveVisibility,
            inalienableParameters,
            session,
            reporter
        )
        withDeclaration(function) {
            body.checkChildrenWithCustomVisitor(it, visitor)
        }
    }

    open val inlineVisitor get() = ::BasicInlineVisitor

    open class BasicInlineVisitor(
        val inlineFunction: FirFunction,
        val inlineFunEffectiveVisibility: EffectiveVisibility,
        val inalienableParameters: List<FirValueParameterSymbol>,
        val session: FirSession,
        val reporter: DiagnosticReporter
    ) : FirDefaultVisitor<Unit, CheckerContext>() {
        private val isEffectivelyPrivateApiFunction: Boolean = inlineFunEffectiveVisibility.privateApi

        private val prohibitProtectedCallFromInline: Boolean =
            session.languageVersionSettings.supportsFeature(LanguageFeature.ProhibitProtectedCallFromInline)

        override fun visitElement(element: FirElement, data: CheckerContext) {}

        override fun visitFunctionCall(functionCall: FirFunctionCall, data: CheckerContext) {
            val targetSymbol = functionCall.toResolvedCallableSymbol()
            if (targetSymbol != null) {
                checkReceiversOfQualifiedAccessExpression(functionCall, targetSymbol, data)
                data.checkArgumentsOfCall(functionCall, targetSymbol)
                data.checkQualifiedAccess(functionCall, targetSymbol)
            }
        }

        override fun visitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression, data: CheckerContext) {
            val targetSymbol = qualifiedAccessExpression.toResolvedCallableSymbol()
            data.checkQualifiedAccess(qualifiedAccessExpression, targetSymbol)
            checkReceiversOfQualifiedAccessExpression(qualifiedAccessExpression, targetSymbol, data)
        }

        // prevent delegation to visitQualifiedAccessExpression, which causes redundant diagnostics
        override fun visitExpressionWithSmartcast(expressionWithSmartcast: FirExpressionWithSmartcast, data: CheckerContext) {}

        override fun visitVariableAssignment(variableAssignment: FirVariableAssignment, data: CheckerContext) {
            val propertySymbol = variableAssignment.calleeReference.toResolvedCallableSymbol() as? FirPropertySymbol ?: return
            val setterSymbol = propertySymbol.setterSymbol ?: return
            data.checkQualifiedAccess(variableAssignment, setterSymbol)
        }

        private fun checkReceiversOfQualifiedAccessExpression(
            qualifiedAccessExpression: FirQualifiedAccessExpression,
            targetSymbol: FirBasedSymbol<*>?,
            context: CheckerContext
        ) {
            context.checkReceiver(qualifiedAccessExpression, qualifiedAccessExpression.dispatchReceiver, targetSymbol)
            context.checkReceiver(qualifiedAccessExpression, qualifiedAccessExpression.extensionReceiver, targetSymbol)
        }

        private fun CheckerContext.checkArgumentsOfCall(
            functionCall: FirFunctionCall,
            targetSymbol: FirBasedSymbol<*>?
        ) {
            val calledFunctionSymbol = targetSymbol as? FirNamedFunctionSymbol ?: return
            val argumentMapping = functionCall.resolvedArgumentMapping ?: return
            for ((wrappedArgument, valueParameter) in argumentMapping) {
                val argument = wrappedArgument.unwrapArgument()
                val resolvedArgumentSymbol = argument.toResolvedCallableSymbol() as? FirVariableSymbol<*> ?: continue

                val valueParameterOfOriginalInlineFunction = inalienableParameters.firstOrNull { it == resolvedArgumentSymbol }
                if (valueParameterOfOriginalInlineFunction != null) {
                    val factory = when {
                        calledFunctionSymbol.isInline -> when {
                            valueParameter.isNoinline -> FirErrors.USAGE_IS_NOT_INLINABLE
                            valueParameter.isCrossinline && !valueParameterOfOriginalInlineFunction.isCrossinline
                            -> FirErrors.NON_LOCAL_RETURN_NOT_ALLOWED
                            else -> continue
                        }
                        else -> FirErrors.USAGE_IS_NOT_INLINABLE
                    }
                    reporter.reportOn(argument.source, factory, valueParameterOfOriginalInlineFunction)
                }
            }
        }

        private fun CheckerContext.checkReceiver(
            qualifiedAccessExpression: FirQualifiedAccessExpression,
            receiverExpression: FirExpression,
            targetSymbol: FirBasedSymbol<*>?
        ) {
            val receiverSymbol = receiverExpression.toResolvedCallableSymbol() ?: return
            if (receiverSymbol in inalienableParameters) {
                if (!isInvokeOrInlineExtension(targetSymbol)) {
                    reporter.reportOn(
                        receiverExpression.source ?: qualifiedAccessExpression.source,
                        FirErrors.USAGE_IS_NOT_INLINABLE,
                        receiverSymbol
                    )
                }
            }
        }

        private fun isInvokeOrInlineExtension(targetSymbol: FirBasedSymbol<*>?): Boolean {
            if (targetSymbol !is FirNamedFunctionSymbol) return false
            // TODO: receivers are currently not inline (KT-5837)
            // if (targetSymbol.isInline) return true
            return targetSymbol.name == OperatorNameConventions.INVOKE &&
                    targetSymbol.dispatchReceiverType?.isBuiltinFunctionalType(session) == true
        }

        private fun CheckerContext.checkQualifiedAccess(
            qualifiedAccess: FirQualifiedAccess,
            targetSymbol: FirBasedSymbol<*>?
        ) {
            val source = qualifiedAccess.source ?: return
            if (targetSymbol !is FirCallableSymbol<*>) return

            if (targetSymbol in inalienableParameters) {
                if (!qualifiedAccess.partOfCall(this)) {
                    reporter.reportOn(source, FirErrors.USAGE_IS_NOT_INLINABLE, targetSymbol)
                }
            }
            checkVisibilityAndAccess(qualifiedAccess, targetSymbol, source)
            checkRecursion(targetSymbol, source)
        }

        private fun FirQualifiedAccess.partOfCall(context: CheckerContext): Boolean {
            if (this !is FirExpression) return false
            val containingQualifiedAccess = context.qualifiedAccessOrAnnotationCalls.getOrNull(
                context.qualifiedAccessOrAnnotationCalls.size - 2
            ) ?: return false
            if (this == (containingQualifiedAccess as? FirQualifiedAccess)?.explicitReceiver) return true
            val call = containingQualifiedAccess as? FirCall ?: return false
            return call.arguments.any { it.unwrapArgument() == this }
        }

        private fun CheckerContext.checkVisibilityAndAccess(
            accessExpression: FirQualifiedAccess,
            calledDeclaration: FirCallableSymbol<*>?,
            source: KtSourceElement
        ) {
            if (
                calledDeclaration == null ||
                calledDeclaration.callableId.callableName == BACKING_FIELD
            ) {
                return
            }
            val recordedEffectiveVisibility = calledDeclaration.publishedApiEffectiveVisibility ?: calledDeclaration.effectiveVisibility
            val calledFunEffectiveVisibility = recordedEffectiveVisibility.let {
                if (it == EffectiveVisibility.Local) {
                    EffectiveVisibility.Public
                } else {
                    it
                }
            }
            val isCalledFunPublicOrPublishedApi = calledFunEffectiveVisibility.publicApi
            val isInlineFunPublicOrPublishedApi = inlineFunEffectiveVisibility.publicApi
            if (isInlineFunPublicOrPublishedApi &&
                !isCalledFunPublicOrPublishedApi &&
                calledDeclaration.visibility !== Visibilities.Local
            ) {
                reporter.reportOn(
                    source,
                    FirErrors.NON_PUBLIC_CALL_FROM_PUBLIC_INLINE,
                    calledDeclaration,
                    inlineFunction.symbol
                )
            } else {
                checkPrivateClassMemberAccess(calledDeclaration, source)
                if (isInlineFunPublicOrPublishedApi) {
                    checkSuperCalls(calledDeclaration, accessExpression)
                }
            }

            val isConstructorCall = calledDeclaration is FirConstructorSymbol
            if (
                isInlineFunPublicOrPublishedApi &&
                inlineFunEffectiveVisibility.toVisibility() !== Visibilities.Protected &&
                calledFunEffectiveVisibility.toVisibility() === Visibilities.Protected
            ) {
                val factory = when {
                    isConstructorCall -> FirErrors.PROTECTED_CONSTRUCTOR_CALL_FROM_PUBLIC_INLINE
                    prohibitProtectedCallFromInline -> FirErrors.PROTECTED_CALL_FROM_PUBLIC_INLINE_ERROR
                    else -> FirErrors.PROTECTED_CALL_FROM_PUBLIC_INLINE
                }
                reporter.reportOn(source, factory, calledDeclaration, inlineFunction.symbol)
            }
        }

        private fun CheckerContext.checkPrivateClassMemberAccess(
            calledDeclaration: FirCallableSymbol<*>,
            source: KtSourceElement
        ) {
            if (!isEffectivelyPrivateApiFunction) {
                if (calledDeclaration.isInsidePrivateClass()) {
                    reporter.reportOn(
                        source,
                        FirErrors.PRIVATE_CLASS_MEMBER_FROM_INLINE,
                        calledDeclaration,
                        inlineFunction.symbol
                    )
                }
            }
        }

        private fun CheckerContext.checkSuperCalls(
            calledDeclaration: FirCallableSymbol<*>,
            callExpression: FirQualifiedAccess
        ) {
            val receiver = callExpression.dispatchReceiver as? FirQualifiedAccessExpression ?: return
            if (receiver.calleeReference is FirSuperReference) {
                val dispatchReceiverType = receiver.dispatchReceiver.typeRef.coneType
                val classSymbol = dispatchReceiverType.toSymbol(this@BasicInlineVisitor.session) ?: return
                if (!classSymbol.isDefinedInInlineFunction()) {
                    reporter.reportOn(
                        callExpression.dispatchReceiver.source,
                        FirErrors.SUPER_CALL_FROM_PUBLIC_INLINE,
                        calledDeclaration
                    )
                }
            }
        }

        private fun FirClassifierSymbol<*>.isDefinedInInlineFunction(): Boolean {
            return when (val symbol = this) {
                is FirAnonymousObjectSymbol -> true
                is FirRegularClassSymbol -> symbol.classId.isLocal
                is FirTypeAliasSymbol, is FirTypeParameterSymbol -> error("Unexpected classifier declaration type: $symbol")
            }
        }

        private fun CheckerContext.checkRecursion(
            targetSymbol: FirBasedSymbol<*>,
            source: KtSourceElement
        ) {
            if (targetSymbol == inlineFunction.symbol) {
                reporter.reportOn(source, FirErrors.RECURSION_IN_INLINE, targetSymbol)
            }
        }

        private fun FirCallableSymbol<*>.isInsidePrivateClass(): Boolean {
            val containingClassSymbol = this.containingClass()?.toSymbol(session) ?: return false

            val containingClassVisibility = when (containingClassSymbol) {
                is FirAnonymousObjectSymbol -> return false
                is FirRegularClassSymbol -> containingClassSymbol.visibility
                is FirTypeAliasSymbol -> containingClassSymbol.visibility
            }
            return containingClassVisibility == Visibilities.Private || containingClassVisibility == Visibilities.PrivateToThis
        }
    }

    protected open fun CheckerContext.checkSuspendFunctionalParameterWithDefaultValue(
        param: FirValueParameter,
        reporter: DiagnosticReporter,
    ) {
    }

    protected open fun CheckerContext.checkFunctionalParametersWithInheritedDefaultValues(
        function: FirSimpleFunction,
        reporter: DiagnosticReporter,
        overriddenSymbols: List<FirCallableSymbol<out FirCallableDeclaration>>,
    ) {
    }

    private fun CheckerContext.checkParameters(
        function: FirSimpleFunction,
        overriddenSymbols: List<FirCallableSymbol<out FirCallableDeclaration>>,
        reporter: DiagnosticReporter
    ) {
        for (param in function.valueParameters) {
            val coneType = param.returnTypeRef.coneType
            val isFunctionalType = coneType.isFunctionalType(session)
            val isSuspendFunctionalType = coneType.isSuspendFunctionType(session)
            val defaultValue = param.defaultValue

            if (!(isFunctionalType || isSuspendFunctionalType) && (param.isNoinline || param.isCrossinline)) {
                reporter.reportOn(param.source, FirErrors.ILLEGAL_INLINE_PARAMETER_MODIFIER)
            }

            if (param.isNoinline) continue

            if (function.isSuspend && defaultValue != null && isSuspendFunctionalType) {
                this.checkSuspendFunctionalParameterWithDefaultValue(param, reporter)
            }

            if (isSuspendFunctionalType && !param.isCrossinline) {
                if (function.isSuspend) {
                    val modifier = param.returnTypeRef.getModifier(KtTokens.SUSPEND_KEYWORD)
                    if (modifier != null) {
                        reporter.reportOn(param.returnTypeRef.source, FirErrors.REDUNDANT_INLINE_SUSPEND_FUNCTION_TYPE)
                    }
                } else {
                    reporter.reportOn(param.source, FirErrors.INLINE_SUSPEND_FUNCTION_TYPE_UNSUPPORTED)
                }
            }

            if (coneType.isNullable && isFunctionalType) {
                reporter.reportOn(
                    param.source,
                    FirErrors.NULLABLE_INLINE_PARAMETER,
                    param.symbol,
                    function.symbol
                )
            }

            if (isFunctionalType && defaultValue != null && !isInlinableDefaultValue(defaultValue)) {
                reporter.reportOn(
                    defaultValue.source,
                    FirErrors.INVALID_DEFAULT_FUNCTIONAL_PARAMETER_FOR_INLINE,
                    defaultValue,
                    param.symbol
                )
            }
        }

        if (overriddenSymbols.isNotEmpty()) {
            for (param in function.typeParameters) {
                if (param.isReified) {
                    reporter.reportOn(param.source, FirErrors.REIFIED_TYPE_PARAMETER_IN_OVERRIDE)
                }
            }
        }

        //check for inherited default values
        this.checkFunctionalParametersWithInheritedDefaultValues(function, reporter, overriddenSymbols)
    }

    protected fun CheckerContext.checkParametersInNotInline(function: FirFunction, reporter: DiagnosticReporter) {
        for (param in function.valueParameters) {
            if (param.isNoinline || param.isCrossinline) {
                reporter.reportOn(param.source, FirErrors.ILLEGAL_INLINE_PARAMETER_MODIFIER)
            }
        }
    }

    private fun FirCallableDeclaration.getOverriddenSymbols(context: CheckerContext): List<FirCallableSymbol<out FirCallableDeclaration>> {
        if (!this.isOverride) return emptyList()
        val classSymbol = this.containingClass()?.toSymbol(context.session) as? FirClassSymbol<*> ?: return emptyList()
        val scope = classSymbol.unsubstitutedScope(context)
        //this call is needed because AbstractFirUseSiteMemberScope collect overrides in it only,
        //and not in processDirectOverriddenFunctionsWithBaseScope
        scope.processFunctionsByName(this.symbol.name) { }
        return scope.getDirectOverriddenMembers(this.symbol, true)
    }

    private fun CheckerContext.checkNothingToInline(function: FirSimpleFunction, reporter: DiagnosticReporter) {
        if (function.isExpect || function.isSuspend) return
        if (function.typeParameters.any { it.symbol.isReified }) return
        val hasInlinableParameters =
            function.valueParameters.any { param ->
                val type = param.returnTypeRef.coneType
                !param.isNoinline && !type.isNullable
                        && (type.isFunctionalType(session) || type.isSuspendFunctionType(session))
            }
        if (hasInlinableParameters) return
        if (function.isInlineOnly()) return

        reporter.reportOn(function.source, FirErrors.NOTHING_TO_INLINE)
    }

    private fun CheckerContext.checkCanBeInlined(
        declaration: FirCallableDeclaration,
        effectiveVisibility: EffectiveVisibility,
        reporter: DiagnosticReporter
    ): Boolean {
        if (declaration.containingClass() == null) return true
        if (effectiveVisibility == EffectiveVisibility.PrivateInClass) return true

        if (!declaration.isFinal) {
            reporter.reportOn(declaration.source, FirErrors.DECLARATION_CANT_BE_INLINED)
            return false
        }
        return true
    }

    private fun isInlinableDefaultValue(expression: FirExpression): Boolean =
        expression is FirCallableReferenceAccess ||
                expression is FirFunctionCall ||
                expression is FirLambdaArgumentExpression ||
                expression is FirAnonymousFunctionExpression ||
                (expression is FirConstExpression<*> && expression.value == null) //this will be reported separately

    fun CheckerContext.checkCallableDeclaration(declaration: FirCallableDeclaration, reporter: DiagnosticReporter) {
        if (declaration is FirPropertyAccessor) return
        val overriddenSymbols = declaration.getOverriddenSymbols(this)
        if (declaration is FirSimpleFunction) {
            checkParameters(declaration, overriddenSymbols, reporter)
            checkNothingToInline(declaration, reporter)
        }
        val canBeInlined = checkCanBeInlined(declaration, declaration.effectiveVisibility, reporter)

        if (canBeInlined && overriddenSymbols.isNotEmpty()) {
            reporter.reportOn(declaration.source, FirErrors.OVERRIDE_BY_INLINE)
        }
    }

    private fun FirElement.checkChildrenWithCustomVisitor(
        parentContext: CheckerContext,
        visitorVoid: FirVisitor<Unit, CheckerContext>
    ) {
        val collectingVisitor = object : AbstractDiagnosticCollectorVisitor(parentContext) {
            override fun checkElement(element: FirElement) {
                element.accept(visitorVoid, context)
            }
        }
        this.accept(collectingVisitor, null)
    }
}
