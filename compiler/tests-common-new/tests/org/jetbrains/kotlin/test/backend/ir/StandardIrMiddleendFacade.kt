/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.ir

import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.services.TestServices

class StandardIrMiddleendFacade(testServices: TestServices) :
    IrMiddleendFacade<IrBackendInput, IrMiddleendOutput>(testServices, BackendKinds.IrBackend, MiddleendKinds.IrBackend) {
    override fun transform(module: TestModule, inputArtifact: IrBackendInput): IrMiddleendOutput {
        return when (inputArtifact) {
            is IrBackendInput.JvmIrBackendInput -> IrMiddleendOutput.JvmIrMiddleendOutput(
                inputArtifact.state,
                inputArtifact.codegenFactory,
                inputArtifact.backendInput,
                inputArtifact.sourceFiles
            )
            is IrBackendInput.JsIrBackendInput -> IrMiddleendOutput.JsIrMiddleendOutput(
                inputArtifact.irModuleFragment,
                inputArtifact.sourceFiles,
                inputArtifact.bindingContext,
                inputArtifact.icData,
                inputArtifact.expectDescriptorToSymbol
            )
        }
    }
}