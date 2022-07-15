/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOnWithSuppression
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.isSealed
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType

object FirSealedSupertypeChecker : FirClassChecker() {
    override fun CheckerContext.check(declaration: FirClass, reporter: DiagnosticReporter) {
        // only the file declaration is present
        if (declaration.classId.isLocal) {
            checkLocalDeclaration(declaration, this, reporter)
        } else {
            this.checkGlobalDeclaration(declaration, reporter)
        }
    }

    private fun CheckerContext.checkGlobalDeclaration(declaration: FirClass, reporter: DiagnosticReporter) {
        val subclassPackage = declaration.classId.packageFqName
        for (superTypeRef in declaration.superTypeRefs) {
            val superClassId = superTypeRef.coneType.classId ?: continue

            if (superClassId.isLocal) {
                continue
            }

            val superClass = session.symbolProvider.getClassLikeSymbolByClassId(superClassId) as? FirRegularClassSymbol ?: continue

            if (!superClass.isSealed) continue
            if (superClass.origin is FirDeclarationOrigin.Java) {
                reporter.reportOnWithSuppression(superTypeRef, FirErrors.CLASS_INHERITS_JAVA_SEALED_CLASS, this)
                continue
            }
            val superClassPackage = superClass.classId.packageFqName
            if (superClassPackage != subclassPackage) {
                reporter.reportOnWithSuppression(superTypeRef, FirErrors.SEALED_INHERITOR_IN_DIFFERENT_PACKAGE, this)
            }
            if (superClass.moduleData != declaration.moduleData) {
                // TODO: implement logic like in org.jetbrains.kotlin.resolve.checkers.SealedInheritorInSameModuleChecker for MPP support.
                reporter.reportOnWithSuppression(superTypeRef, FirErrors.SEALED_INHERITOR_IN_DIFFERENT_MODULE, this)
            }
        }
    }

    private fun checkLocalDeclaration(declaration: FirClass, context: CheckerContext, reporter: DiagnosticReporter) {
        for (superTypeRef in declaration.superTypeRefs) {
            val classId = superTypeRef.coneType.classId ?: continue

            if (classId.isLocal) {
                continue
            }

            val superClassSymbol = context.session.symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol ?: continue

            if (superClassSymbol.modality == Modality.SEALED) {
                val declarationType = if (declaration is FirAnonymousObject) "Anonymous object" else "Local class"
                reporter.reportOnWithSuppression(
                    superTypeRef,
                    FirErrors.SEALED_SUPERTYPE_IN_LOCAL_CLASS,
                    declarationType,
                    superClassSymbol.classKind,
                    context
                )
                return
            }
        }
    }
}
