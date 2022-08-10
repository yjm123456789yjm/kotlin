/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.runners.codegen

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.classic.ClassicBackendInput
import org.jetbrains.kotlin.test.backend.classic.ClassicMiddleendOutput
import org.jetbrains.kotlin.test.backend.classic.ClassicJvmBackendFacade
import org.jetbrains.kotlin.test.backend.handlers.AsmLikeInstructionListingHandler
import org.jetbrains.kotlin.test.backend.ir.*
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureClassicFrontendHandlersStep
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureJvmArtifactsHandlersStep
import org.jetbrains.kotlin.test.directives.AsmLikeInstructionListingDirectives.CHECK_ASM_LIKE_INSTRUCTIONS
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontend2ClassicBackendConverter
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontend2IrConverter
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendFacade
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendOutputArtifact
import org.jetbrains.kotlin.test.frontend.classic.handlers.ClassicDiagnosticsHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirDiagnosticsHandler
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerWithTargetBackendTest

abstract class AbstractAsmLikeInstructionListingTestBase<R : ResultingArtifact.FrontendOutput<R>, I : ResultingArtifact.BackendInput<I>, O : ResultingArtifact.MiddleendOutput<O>>(
    val targetFrontend: FrontendKind<R>,
    targetBackend: TargetBackend
) : AbstractKotlinCompilerWithTargetBackendTest(targetBackend) {
    abstract val frontendFacade: Constructor<FrontendFacade<R>>
    abstract val frontendToBackendConverter: Constructor<Frontend2BackendConverter<R, I>>
    abstract val middleendFacade: Constructor<MiddleendFacade<I, O>>
    abstract val backendFacade: Constructor<BackendFacade<O, BinaryArtifacts.Jvm>>

    override fun TestConfigurationBuilder.configuration() {
        defaultDirectives {
            +CHECK_ASM_LIKE_INSTRUCTIONS
        }

        commonConfigurationForCodegenTest(targetFrontend, frontendFacade, frontendToBackendConverter, middleendFacade, backendFacade)

        configureClassicFrontendHandlersStep {
            useHandlers(
                ::ClassicDiagnosticsHandler
            )
        }

        configureFirHandlersStep {
            useHandlers(
                ::FirDiagnosticsHandler
            )
        }

        configureJvmArtifactsHandlersStep {
            useHandlers(
                ::AsmLikeInstructionListingHandler
            )
        }

        useAfterAnalysisCheckers(::BlackBoxCodegenSuppressor)
    }
}

open class AbstractAsmLikeInstructionListingTest :
    AbstractAsmLikeInstructionListingTestBase<ClassicFrontendOutputArtifact, ClassicBackendInput, ClassicMiddleendOutput>(
        FrontendKinds.ClassicFrontend,
        TargetBackend.JVM
    ) {

    override val frontendFacade: Constructor<FrontendFacade<ClassicFrontendOutputArtifact>>
        get() = ::ClassicFrontendFacade

    override val frontendToBackendConverter: Constructor<Frontend2BackendConverter<ClassicFrontendOutputArtifact, ClassicBackendInput>>
        get() = ::ClassicFrontend2ClassicBackendConverter

    override val middleendFacade: Constructor<MiddleendFacade<ClassicBackendInput, ClassicMiddleendOutput>>
        get() = ::ClassicIrMiddleendFacade

    override val backendFacade: Constructor<BackendFacade<ClassicMiddleendOutput, BinaryArtifacts.Jvm>>
        get() = ::ClassicJvmBackendFacade
}

open class AbstractIrAsmLikeInstructionListingTest :
    AbstractAsmLikeInstructionListingTestBase<ClassicFrontendOutputArtifact, IrBackendInput, IrMiddleendOutput>(
        FrontendKinds.ClassicFrontend,
        TargetBackend.JVM_IR
    ) {

    override val frontendFacade: Constructor<FrontendFacade<ClassicFrontendOutputArtifact>>
        get() = ::ClassicFrontendFacade

    override val frontendToBackendConverter: Constructor<Frontend2BackendConverter<ClassicFrontendOutputArtifact, IrBackendInput>>
        get() = ::ClassicFrontend2IrConverter

    override val middleendFacade: Constructor<MiddleendFacade<IrBackendInput, IrMiddleendOutput>>
        get() = ::StandardIrMiddleendFacade

    override val backendFacade: Constructor<BackendFacade<IrMiddleendOutput, BinaryArtifacts.Jvm>>
        get() = ::JvmIrBackendFacade
}

