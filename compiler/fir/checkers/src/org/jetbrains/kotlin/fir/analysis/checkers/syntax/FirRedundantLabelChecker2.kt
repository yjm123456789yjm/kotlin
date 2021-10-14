/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.syntax

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.fir.FirFakeSourceElementKind
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.fir.analysis.getParents
import org.jetbrains.kotlin.fir.analysis.getPrevSiblings
import org.jetbrains.kotlin.fir.analysis.unwrap
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.lexer.KtTokens

object FirRedundantLabelChecker2 {
    private val elementTypesThatCanHaveLabels =
        setOf(KtNodeTypes.LAMBDA_EXPRESSION, KtNodeTypes.FOR, KtNodeTypes.WHILE, KtNodeTypes.DO_WHILE, KtNodeTypes.FUN)
    private val elementTypesThatArePartOfPassThroughElements = setOf(KtNodeTypes.ANNOTATION_ENTRY)

    private val passThroughElementTypes =
        setOf(KtNodeTypes.LABELED_EXPRESSION, KtNodeTypes.ANNOTATED_EXPRESSION, KtNodeTypes.PARENTHESIZED, KtNodeTypes.OBJECT_LITERAL)

    object DeclarationChecker : FirDeclarationSyntaxChecker<FirDeclaration, PsiElement>() {
        override fun checkPsiOrLightTree(
            element: FirDeclaration,
            source: FirSourceElement,
            context: CheckerContext,
            reporter: DiagnosticReporter
        ) {
            // TODO: check contracts
            check(source, reporter, context)
        }
    }

    object ExpressionChecker : FirExpressionSyntaxChecker<FirStatement, PsiElement>() {
        override fun checkPsiOrLightTree(
            element: FirStatement,
            source: FirSourceElement,
            context: CheckerContext,
            reporter: DiagnosticReporter
        ) {
            check(source, reporter, context)
            // LHS of assignment operators are not visited during FIR traversal so we manually check for those.
            val assignmentOp = source.getChild(KtNodeTypes.OPERATION_REFERENCE, depth = 1)
            if (source.kind !is FirFakeSourceElementKind &&
                source.elementType == KtNodeTypes.BINARY_EXPRESSION &&
                assignmentOp?.getChild(KtTokens.ALL_ASSIGNMENTS, depth = 1) != null
            ) {
                assignmentOp.getPrevSiblings().firstOrNull { it.elementType !in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET }?.let {
                    check(it.unwrap(), reporter, context)
                }
            }
        }
    }

    private fun check(
        source: FirSourceElement,
        reporter: DiagnosticReporter,
        context: CheckerContext
    ) {
        if (source.kind is FirFakeSourceElementKind ||
            source.elementType in elementTypesThatCanHaveLabels ||
            source.elementType in elementTypesThatArePartOfPassThroughElements
        ) return
        source.getParents()
            .takeWhile { it.elementType in passThroughElementTypes }
            .forEach {
                if (it.elementType == KtNodeTypes.LABELED_EXPRESSION) {
                    it.getChild(KtNodeTypes.LABEL_QUALIFIER, depth = 1)?.getChild(KtNodeTypes.LABEL, depth = 1)?.let { labelSource ->
                        reporter.reportOn(labelSource, FirErrors.REDUNDANT_LABEL_WARNING, context)
                    }
                }
            }
    }
}