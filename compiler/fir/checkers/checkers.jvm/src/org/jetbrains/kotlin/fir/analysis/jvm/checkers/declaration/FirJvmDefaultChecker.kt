/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.jvm.checkers.declaration

import org.jetbrains.kotlin.config.JvmDefaultMode
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.findClosest
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.analysis.diagnostics.jvm.FirJvmErrors
import org.jetbrains.kotlin.fir.analysis.jvm.checkers.isCompiledToJvmDefault
import org.jetbrains.kotlin.fir.analysis.jvm.checkers.isJvm6
import org.jetbrains.kotlin.fir.containingClass
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isInterface
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.java.jvmDefaultModeState
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.getDirectOverriddenFunctions
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirIntersectionCallableSymbol
import org.jetbrains.kotlin.name.JvmNames.JVM_DEFAULT_CLASS_ID
import org.jetbrains.kotlin.name.JvmNames.JVM_DEFAULT_NO_COMPATIBILITY_CLASS_ID
import org.jetbrains.kotlin.name.JvmNames.JVM_DEFAULT_WITH_COMPATIBILITY_CLASS_ID

object FirJvmDefaultChecker : FirBasicDeclarationChecker() {
    override fun CheckerContext.check(declaration: FirDeclaration, reporter: DiagnosticReporter) {
        val jvmDefaultMode = session.jvmDefaultModeState
        val defaultAnnotation = declaration.getAnnotationByClassId(JVM_DEFAULT_CLASS_ID)

        if (defaultAnnotation != null) {
            val containingDeclaration = findClosest<FirClassLikeDeclaration>()
            val source = defaultAnnotation.source
            when {
                containingDeclaration !is FirClass || !containingDeclaration.isInterface -> {
                    reporter.reportOn(source, FirJvmErrors.JVM_DEFAULT_NOT_IN_INTERFACE)
                    return
                }
                isJvm6() -> {
                    reporter.reportOn(source, FirJvmErrors.JVM_DEFAULT_IN_JVM6_TARGET, "JvmDefault")
                    return
                }
                !jvmDefaultMode.isEnabled -> {
                    reporter.reportOn(source, FirJvmErrors.JVM_DEFAULT_IN_DECLARATION, "JvmDefault")
                    return
                }
            }
        } else {
            val annotationNoCompatibility = declaration.getAnnotationByClassId(JVM_DEFAULT_NO_COMPATIBILITY_CLASS_ID)
            if (annotationNoCompatibility != null) {
                val source = annotationNoCompatibility.source
                when {
                    isJvm6() -> {
                        reporter.reportOn(
                            source,
                            FirJvmErrors.JVM_DEFAULT_IN_JVM6_TARGET,
                            "JvmDefaultWithoutCompatibility"
                        )
                        return
                    }
                    !jvmDefaultMode.isEnabled -> {
                        reporter.reportOn(
                            source,
                            FirJvmErrors.JVM_DEFAULT_IN_DECLARATION,
                            "JvmDefaultWithoutCompatibility"
                        )
                        return
                    }
                }
            }
            val annotationWithCompatibility = declaration.getAnnotationByClassId(JVM_DEFAULT_WITH_COMPATIBILITY_CLASS_ID)
            if (annotationWithCompatibility != null) {
                val source = annotationWithCompatibility.source
                when {
                    isJvm6() -> {
                        reporter.reportOn(
                            source,
                            FirJvmErrors.JVM_DEFAULT_IN_JVM6_TARGET,
                            "JvmDefaultWithCompatibility"
                        )
                        return
                    }
                    jvmDefaultMode != JvmDefaultMode.ALL_INCOMPATIBLE -> {
                        reporter.reportOn(source, FirJvmErrors.JVM_DEFAULT_WITH_COMPATIBILITY_IN_DECLARATION)
                        return
                    }
                    declaration !is FirRegularClass || !declaration.isInterface -> {
                        reporter.reportOn(source, FirJvmErrors.JVM_DEFAULT_WITH_COMPATIBILITY_NOT_ON_INTERFACE)
                        return
                    }
                }
            }
        }

        this.checkNonJvmDefaultOverridesJavaDefault(defaultAnnotation, jvmDefaultMode, declaration, reporter)
    }

    private fun CheckerContext.checkNonJvmDefaultOverridesJavaDefault(
        defaultAnnotation: FirAnnotation?,
        jvmDefaultMode: JvmDefaultMode,
        declaration: FirDeclaration,
        reporter: DiagnosticReporter
    ) {
        if (defaultAnnotation != null || jvmDefaultMode.forAllMethodsWithBody) return
        val member = declaration as? FirSimpleFunction ?: return

        val containingDeclaration = findClosest<FirClassLikeDeclaration>()
        if (containingDeclaration is FirClass && containingDeclaration.isInterface) {
            val unsubstitutedScope = containingDeclaration.unsubstitutedScope(this)
            unsubstitutedScope.processFunctionsByName(member.name) {}
            val overriddenFunctions = unsubstitutedScope.getDirectOverriddenFunctions(member.symbol)

            if (overriddenFunctions.any { it.getAnnotationByClassId(JVM_DEFAULT_CLASS_ID) != null }) {
                reporter.reportOn(declaration.source, FirJvmErrors.JVM_DEFAULT_REQUIRED_FOR_OVERRIDE)
            } else if (jvmDefaultMode.isEnabled) {
                for (overriddenFunction in overriddenFunctions) {
                    val overriddenDeclarations = overriddenFunction.getOverriddenDeclarations()
                    for (overriddenDeclaration in overriddenDeclarations) {
                        val containingClassSymbol = overriddenDeclaration.containingClass()?.toSymbol(session)
                        if (containingClassSymbol?.origin is FirDeclarationOrigin.Java &&
                            overriddenDeclaration.modality != Modality.ABSTRACT
                        ) {
                            reporter.reportOn(declaration.source, FirJvmErrors.NON_JVM_DEFAULT_OVERRIDES_JAVA_DEFAULT)
                            return
                        }
                    }
                }
            }
        }
    }

    fun FirCallableSymbol<*>.getOverriddenDeclarations(): List<FirCallableSymbol<*>> {
        return if (this is FirIntersectionCallableSymbol) {
            ArrayList(this.intersections)
        } else {
            ArrayList<FirCallableSymbol<*>>(1).also { it.add(this) }
        }
    }

    fun FirCallableSymbol<*>.isCompiledToJvmDefaultWithProperMode(jvmDefaultMode: JvmDefaultMode): Boolean {
        // TODO: Fix support for all cases
        return isCompiledToJvmDefault(jvmDefaultMode)
    }
}
