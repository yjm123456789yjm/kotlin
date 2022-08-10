/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.classic

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.model.MiddleendKinds
import org.jetbrains.kotlin.test.model.ResultingArtifact

// Old backend (JVM and JS)
data class ClassicMiddleendOutput(
    val psiFiles: Collection<KtFile>,
    val analysisResult: AnalysisResult,
    val project: Project,
    val languageVersionSettings: LanguageVersionSettings
) : ResultingArtifact.MiddleendOutput<ClassicMiddleendOutput>() {
    override val kind: MiddleendKinds.ClassicBackend
        get() = MiddleendKinds.ClassicBackend
}