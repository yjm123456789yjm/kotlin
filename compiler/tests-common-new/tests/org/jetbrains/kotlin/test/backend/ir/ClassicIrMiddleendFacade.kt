/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.ir

import org.jetbrains.kotlin.test.backend.classic.ClassicBackendInput
import org.jetbrains.kotlin.test.backend.classic.ClassicMiddleendOutput
import org.jetbrains.kotlin.test.model.BackendKinds
import org.jetbrains.kotlin.test.model.MiddleendKinds
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices

class ClassicIrMiddleendFacade(testServices: TestServices) :
    IrMiddleendFacade<ClassicBackendInput, ClassicMiddleendOutput>(testServices, BackendKinds.ClassicBackend, MiddleendKinds.ClassicBackend) {
    override fun transform(module: TestModule, inputArtifact: ClassicBackendInput): ClassicMiddleendOutput {
        return ClassicMiddleendOutput(inputArtifact.psiFiles, inputArtifact.analysisResult, inputArtifact.project, inputArtifact.languageVersionSettings)
    }
}