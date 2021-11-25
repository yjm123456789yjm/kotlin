/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol

import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.impl.barebone.test.expressionMarkerProvider
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.light.classes.symbol.test.base.AbstractFirLightClassSingleFileTest
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.services.TestModuleStructure
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions

abstract class AbstractReferenceToFirLightClassTest : AbstractFirLightClassSingleFileTest() {
    override fun doTestByFileStructure(ktFile: KtFile, moduleStructure: TestModuleStructure, testServices: TestServices) {
        val caretPosition = testServices.expressionMarkerProvider.getCaretPosition(ktFile)
        val ktReferences = findReferencesAtCaret(ktFile, caretPosition)
        if (ktReferences.isEmpty()) {
            testServices.assertions.fail { "No references at caret found" }
        }
        val declaration = PsiTreeUtil.findElementOfClassAtOffset(ktFile, caretPosition, KtDeclaration::class.java, false) ?: ktFile
        val actual = computeActual(declaration, ktReferences)
        testServices.assertions.assertEqualsToTestDataFileSibling(actual)
    }

    abstract fun computeActual(declaration: KtAnnotated, ktReferences: List<KtReference>): String

    private fun findReferencesAtCaret(mainKtFile: KtFile, caretPosition: Int): List<KtReference> =
        mainKtFile.findReferenceAt(caretPosition)?.unwrapMultiReferences().orEmpty().filterIsInstance<KtReference>()

    private fun PsiReference.unwrapMultiReferences(): List<PsiReference> = when (this) {
        is KtReference -> listOf(this)
        is PsiMultiReference -> references.flatMap { it.unwrapMultiReferences() }
        else -> error("Unexpected reference $this")
    }
}
