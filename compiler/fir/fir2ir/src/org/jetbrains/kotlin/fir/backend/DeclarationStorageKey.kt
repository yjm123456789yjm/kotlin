/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.utils.addToStdlib.runUnless

class DeclarationStorageKey(private val declaration: FirDeclaration, components: Fir2IrComponents) {
    private val signature = runUnless(
        declaration is FirAnonymousInitializer ||
                (declaration as? FirMemberDeclaration)?.effectiveVisibility == EffectiveVisibility.Local
    ) { components.signatureComposer.composeSignature(declaration) }

    override fun equals(other: Any?): Boolean {
        if (other !is DeclarationStorageKey) return false
        if (signature != null) {
            return signature == other.signature
        }
        return declaration == other.declaration
    }

    override fun hashCode(): Int {
        if (signature != null) {
            return signature.hashCode()
        }
        return declaration.hashCode()
    }
}
