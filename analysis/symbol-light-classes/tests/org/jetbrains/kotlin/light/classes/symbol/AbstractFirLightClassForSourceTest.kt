/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol

import org.jetbrains.kotlin.analysis.api.impl.base.test.executeOnPooledThreadInReadAction
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.renderClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractFirLightClassForSourceTest : AbstractReferenceToFirLightClassTest() {
    override fun computeActual(declaration: KtAnnotated, ktReferences: List<KtReference>): String = executeOnPooledThreadInReadAction {
        analyseForTest(declaration) {
            val symbols = ktReferences.flatMap { it.resolveToSymbols() }
            if (symbols.isEmpty()) {
                return@analyseForTest "Unresolved: $ktReferences"
            }
            val symbol = symbols.singleOrNull() ?: return@analyseForTest "Ambiguous resolution: $symbols"
            val parent = if (symbol is KtConstructorSymbol) symbol.psi else symbol.psi?.parent
            val lightClass = when (parent) {
                is KtFile -> parent.findFacadeClass()
                is KtClassBody -> (parent.parent as? KtClass)?.toLightClass()
                is KtClass -> parent.toLightClass()
                else -> null
            } ?: return@analyseForTest "Can't find light class for ${symbol.javaClass}"
            lightClass.renderClass()
        }
    }
}
