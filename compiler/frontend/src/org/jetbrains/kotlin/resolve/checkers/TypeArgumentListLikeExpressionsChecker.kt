/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.checkers

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.*

object TypeArgumentListLikeExpressionsChecker : DeclarationChecker {

    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (context.languageVersionSettings.supportsFeature(LanguageFeature.AllowTypeArgumentListLikeExpressions)) return
        if (declaration.parent !is KtFile) return

        val visitor = object : KtTreeVisitorVoid() {
            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                super.visitBinaryExpression(expression)

                check(expression)
            }

            override fun visitUserType(type: KtUserType) {
                super.visitUserType(type)

                check(type)
            }

            private fun check(element: KtElement) {
                val errorElement = element.getChildOfElementType(KtNodeTypes.TYPE_ARGUMENT_LIST_LIKE_EXPRESSION)
                if (errorElement != null) {
                    context.trace.report(Errors.TYPE_ARGUMENT_LIST_LIKE_EXPRESSION.on(errorElement))
                }
            }
        }

        declaration.accept(visitor)
    }

    private fun PsiElement.getChildOfElementType(elementType: IElementType): PsiElement? {
        var current = firstChild
        while (current != null) {
            if (current.node.elementType == elementType) return current

            current = current.nextSibling
        }
        return null
    }
}