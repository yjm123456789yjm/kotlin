/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.ir

import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.test.model.MiddleendKind
import org.jetbrains.kotlin.test.model.MiddleendKinds
import org.jetbrains.kotlin.test.model.ResultingArtifact

sealed class KLibMiddleendOutput : ResultingArtifact.MiddleendOutput<KLibMiddleendOutput>() {
    override val kind: MiddleendKind<KLibMiddleendOutput>
        get() = MiddleendKinds.KLibIrBackend

    abstract val irModuleFragment: IrModuleFragment

    data class JvmKLibIrMiddleendOutput(
        val state: GenerationState,
        val codegenFactory: JvmIrCodegenFactory,
        val backendInput: JvmIrCodegenFactory.JvmIrBackendInput,
        val sourceFiles: List<KtSourceFile>
    ) : KLibMiddleendOutput() {
        override val irModuleFragment: IrModuleFragment
            get() = backendInput.irModuleFragment
    }
}