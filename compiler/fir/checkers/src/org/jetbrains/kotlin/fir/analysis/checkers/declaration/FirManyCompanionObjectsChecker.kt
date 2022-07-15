/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOnWithSuppression
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion

object FirManyCompanionObjectsChecker : FirRegularClassChecker() {
    override fun CheckerContext.check(declaration: FirRegularClass, reporter: DiagnosticReporter) {
        var hasCompanion = false

        for (declaration in declaration.declarations) {
            if (declaration is FirRegularClass && declaration.isCompanion) {
                if (hasCompanion) {
                    reporter.reportOnWithSuppression(declaration, FirErrors.MANY_COMPANION_OBJECTS, this)
                }
                hasCompanion = true
            }
        }
    }
}
