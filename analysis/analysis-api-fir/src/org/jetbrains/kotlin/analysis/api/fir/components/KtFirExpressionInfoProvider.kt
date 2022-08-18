/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.components

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.KtSimpleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.calls.KtSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.components.KtExpressionInfoProvider
import org.jetbrains.kotlin.analysis.api.fir.KtFirAnalysisSession
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.getOrBuildFirSafe
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.fir.declarations.FirErrorFunction
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.resolve.transformers.FirWhenExhaustivenessTransformer
import org.jetbrains.kotlin.psi.*

internal class KtFirExpressionInfoProvider(
    override val analysisSession: KtFirAnalysisSession,
    override val token: KtLifetimeToken,
) : KtExpressionInfoProvider(), KtFirAnalysisSessionComponent {
    override fun getReturnExpressionTargetSymbol(returnExpression: KtReturnExpression): KtCallableSymbol? {
        val fir = returnExpression.getOrBuildFirSafe<FirReturnExpression>(firResolveSession) ?: return null
        val firTargetSymbol = fir.target.labeledElement
        if (firTargetSymbol is FirErrorFunction) return null
        return firSymbolBuilder.callableBuilder.buildCallableSymbol(firTargetSymbol.symbol)
    }

    override fun getWhenMissingCases(whenExpression: KtWhenExpression): List<WhenMissingCase> {
        val firWhenExpression = whenExpression.getOrBuildFirSafe<FirWhenExpression>(analysisSession.firResolveSession) ?: return emptyList()
        return FirWhenExhaustivenessTransformer.computeAllMissingCases(analysisSession.firResolveSession.useSiteFirSession, firWhenExpression)
    }


    override fun isUsedAsExpression(expression: KtExpression): Boolean =
        isUsed(expression)

    private fun isUsed(psiElement: PsiElement): Boolean {
        return when (psiElement) {
            // TODO: How defensive do we want to be?
            // !is KtExpression ->
            //     TODO("Non-KtExpression expression in isUsedAsExpression")

            /**
             * DECLARATIONS
             */
            // Inner PSI of KtLambdaExpressions. Used if the containing KtLambdaExpression is.
            is KtFunctionLiteral ->
                doesParentUseChild(psiElement.parent, psiElement)

            // KtNamedFunction includes `fun() { ... }` lambda syntax. No other
            // "named" functions can be expressions.
            is KtNamedFunction ->
                doesParentUseChild(psiElement.parent, psiElement)

            // No other declarations are considered expressions
            is KtDeclaration ->
                false

            /**
             * EXPRESSIONS
             */
            // A handful of expression are never considered used:

            //  - Everything of type `Nothing`
            is KtThrowExpression ->
                false
            is KtReturnExpression ->
                false
            is KtBreakExpression ->
                false
            is KtContinueExpression ->
                false

            // - Loops
            is KtLoopExpression ->
                false

            // - The `this` in `constructor(x: Int) : this(x)`
            is KtConstructorDelegationReferenceExpression ->
                false

            // - Administrative node for EnumEntries. Never used as expression.
            is KtEnumEntrySuperclassReferenceExpression ->
                false

            // - The "reference" in a constructor call. E.g. `C` in `C()`
            is KtConstructorCalleeExpression ->
                false

            // - Labels themselves: `@label` in return`@label` or `label@`while...
            is KtLabelReferenceExpression ->
                false

            // - The operation symbol itself in binary and unary operations: `!!`, `+`...
            is KtOperationReferenceExpression ->
                false

            // All other expressions are used if their parent expression uses them.
            else ->
                doesParentUseChild(psiElement.parent, psiElement)
        }
    }

    /**
     * Doc is WIP!
     *
     * Remark that
     *   - it does more than check parent use of child -- it recurses on the parent, sometimes!
     *   - the degree of robustness/defensiveness employed is not quite clear yet (false positives/negatives?)
     */
    private fun doesParentUseChild(parent: PsiElement, child: PsiElement): Boolean {
        return when (parent) {
            /**
             * NON-EXPRESSION PARENTS
             */

            is KtValueArgument ->
                parent.getArgumentExpression() == child

            is KtContainerNode ->
                // !!!!CAUTION!!!! Not `parentUse(parent.parent, _parent_)`
                // Here we assume the parent (e.g. If condition) statement
                // ignores the ContainerNode when accessing child
                doesParentUseChild(parent.parent, child)

            is KtWhenEntry ->
                (parent.expression == child &&
                        isUsed(parent.parent)) ||
                        (child in parent.conditions)
            is KtWhenCondition ->
                doesParentUseChild(parent.parent, parent)

            // Type parameters, return types and other annotations are all contained in KtUserType,
            // and are never considered used as expressions
            is KtUserType ->
                false

            // Top-level named funs have KtFile Parent
            is KtFile ->
                false
            is KtScript ->
                TODO("Can't test this")

            // class members have KtClassBody parents
            is KtClassBody ->
                false

            // $_ and ${_} contexts.
            is KtStringTemplateEntry ->
                parent.expression == child

            // Catch blocks are used if the parent try is
            is KtCatchClause ->
                doesParentUseChild(parent.parent, parent)

            // Finally blocks are never used
            is KtFinallySection ->
                false

            !is KtExpression ->
                error("Unhandled Non-KtExpression parent of KtExpression: ${parent::class}")

            /**
             * EXPRESSIONS
             */
            is KtEnumEntry ->
                TODO("Can't hit with test -- has no child expressions?")
            is KtTypeParameter ->
                TODO("Can't hit with test -- has no child expressions. Maybe blocked by KtUserType above?")
            is KtLambdaExpression ->
                TODO("Can't hit with test")
            is KtScriptInitializer ->
                TODO("Can't test this")

            is KtBlockExpression ->
                parent.statements.lastOrNull() == child && isUsed(parent)
            is KtFunctionLiteral ->
                (parent.bodyBlockExpression == child) &&
                        analyze(parent) {
                            !parent.getReturnKtType().isUnit
                        }
            is KtDestructuringDeclaration ->
                parent.initializer == child
            is KtBackingField ->
                parent.initializer == child
            is KtPropertyAccessor ->
                (parent.isSetter && parent.bodyExpression == child) ||
                        (parent.isGetter && parent.bodyExpression == child && analyze(parent) {
                            (child as KtExpression).getKtType() == parent.getKtType()
                        })
            is KtNamedFunction ->
                when {
                    parent.bodyExpression != child ->
                        false
                    parent.bodyBlockExpression == child ->
                        analyze(parent) {
                            !parent.getReturnKtType().isUnit
                        }
                    // parent.bodyExpression == child ->
                    else ->
                        analyze(parent) {
                            !parent.getReturnKtType().isUnit
                                    || (child as? KtExpression)?.getKtType()?.isUnit == true
                                    || (child as? KtLambdaExpression)?.getExpectedType()?.isUnit == true
                        }
                }

            is KtParameter ->
                parent.defaultValue == child
            is KtVariableDeclaration ->
                parent.initializer == child
            is KtBinaryExpression ->
                parent.left == child || parent.right == child
            is KtBinaryExpressionWithTypeRHS ->
                parent.left == child
            is KtIsExpression ->
                parent.leftHandSide == child
            is KtUnaryExpression ->
                parent.baseExpression == child
            is KtQualifiedExpression ->
                parent.receiverExpression == child || (parent.selectorExpression == child && isUsed(parent))
            is KtArrayAccessExpression ->
                child in parent.indexExpressions || parent.arrayExpression == child
            is KtCallExpression ->
                parent.calleeExpression == child && (child !is KtReferenceExpression ||
                        // resolution needed to discern the difference between
                        // fun f() { 54 }; f()
                        // val f = { 54 }; f()
                        // in which the `f` in the second case is regarded as used,
                        // the first is not.
                        analyze(child) {
                            when (val resolution = child.resolveCall()) {
                                is KtSuccessCallInfo ->
                                    resolution.call is KtSimpleVariableAccessCall
                                else ->
                                    false
                            }
                        })
            is KtCollectionLiteralExpression ->
                child in parent.getInnerExpressions()
            is KtAnnotatedExpression ->
                parent.baseExpression == child && isUsed(parent)
            is KtDoubleColonExpression ->
                parent.lhs == child && (child !is KtReferenceExpression ||
                        // LHS of _::class is considered unused unless local variable
                        analyze(child) {
                            when (val resolution = child.resolveCall()) {
                                is KtSuccessCallInfo ->
                                    resolution.call is KtSimpleVariableAccessCall
                                else ->
                                    false
                            }
                        })
            is KtParenthesizedExpression ->
                doesParentUseChild(parent.parent, parent)
            is KtWhenExpression ->
                parent.subjectExpression == child && parent.entries.firstOrNull()?.isElse == false
            is KtThrowExpression ->
                parent.thrownExpression == child
            is KtTryExpression ->
                (parent.tryBlock == child || child in parent.catchClauses || parent.finallyBlock == child) && isUsed(parent)
            is KtIfExpression ->
                parent.condition == child ||
                        ((parent.then == child ||
                                parent.`else` == child) && isUsed(parent))
            is KtForExpression ->
                parent.loopRange == child
            // While, DoWhile loops
            is KtWhileExpressionBase ->
                parent.condition == child
            is KtReturnExpression ->
                parent.returnedExpression == child
            is KtLabeledExpression ->
                parent.baseExpression == child && isUsed(parent)

            // No children. TODO: Error instead? Or just false?
            is KtConstantExpression ->
                false

            // no children of class and script initializers are used
            is KtAnonymousInitializer ->
                false

            // no child expressions of primary constructors.
            is KtPrimaryConstructor ->
                false // error?
            // no children of secondary constructs are used.
            is KtSecondaryConstructor ->
                false

            // KtClass, KtObjectDeclaration, KtTypeAlias has no expression children
            is KtClassLikeDeclaration ->
                false // has no expression children

            // Simple names do not have expression children
            // Labels, operations, references by name
            // TODO: Error instead?
            is KtSimpleNameExpression ->
                false

            // this/super in constructor delegations. No expression children
            // TODO: Cannot hit with tests
            is KtConstructorDelegationReferenceExpression ->
                false

            // TODO: cannot write test code to hit this case -- no direct expression children of object literals
            is KtObjectLiteralExpression ->
                false

            // break, continue, super, this do not have children
            is KtBreakExpression ->
                false
            is KtContinueExpression ->
                false
            is KtSuperExpression ->
                false
            is KtThisExpression ->
                false

            // No direct expression children
            // TODO: Error?
            is KtStringTemplateExpression ->
                false

            else ->
                error("Unhandled KtElement subtype: ${parent::class}")
        }
    }
}