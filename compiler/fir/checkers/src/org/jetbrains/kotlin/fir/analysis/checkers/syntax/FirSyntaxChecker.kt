/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.syntax

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtPsiSourceElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.expressions.FirStatement

/**
 * Checker that heavily relies on source tree. So it may have different implementation for each tree variant. Subclass should either
 *
 * - implement `checkPsiOrLightTree` in an AST-agnostic manner, or
 * - implement both `checkPsi` and `checkLightTree` if it's difficult to handle PSI and LT tree in a unified way.
 */
interface FirSyntaxChecker<in D : FirElement, P : PsiElement> {

    fun CheckerContext.checkSyntax(element: D, reporter: DiagnosticReporter) {
        val source = element.source ?: return
        if (!isApplicable(element, source)) return
        @Suppress("UNCHECKED_CAST")
        when (source) {
            is KtPsiSourceElement -> this.checkPsi(element, source, source.psi as P, reporter)
            is KtLightSourceElement -> this.checkLightTree(element, source, reporter)
        }
    }

    fun isApplicable(element: D, source: KtSourceElement): Boolean = true

    fun CheckerContext.checkPsi(element: D, source: KtPsiSourceElement, psi: P, reporter: DiagnosticReporter) {
        this.checkPsiOrLightTree(element, source, reporter)
    }

    fun CheckerContext.checkLightTree(element: D, source: KtLightSourceElement, reporter: DiagnosticReporter) {
        this.checkPsiOrLightTree(element, source, reporter)
    }

    /**
     *  By default psi tree should be equivalent to light tree and can be processed the same way.
     */
    fun CheckerContext.checkPsiOrLightTree(element: D, source: KtSourceElement, reporter: DiagnosticReporter) {}
}

abstract class FirDeclarationSyntaxChecker<in D : FirDeclaration, P : PsiElement> :
    FirDeclarationChecker<D>(),
    FirSyntaxChecker<D, P> {
    final override fun CheckerContext.check(declaration: D, reporter: DiagnosticReporter) {
        this.checkSyntax(declaration, reporter)
    }
}

abstract class FirExpressionSyntaxChecker<in E : FirStatement, P : PsiElement> :
    FirExpressionChecker<E>(),
    FirSyntaxChecker<E, P> {
    final override fun CheckerContext.check(expression: E, reporter: DiagnosticReporter) {
        this.checkSyntax(expression, reporter)
    }
}
