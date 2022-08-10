/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.runners

import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.classic.ClassicBackendInput
import org.jetbrains.kotlin.test.backend.classic.ClassicMiddleendOutput
import org.jetbrains.kotlin.test.backend.classic.ClassicJvmBackendFacade
import org.jetbrains.kotlin.test.backend.handlers.JvmBackendDiagnosticsHandler
import org.jetbrains.kotlin.test.backend.ir.*
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.classicFrontendHandlersStep
import org.jetbrains.kotlin.test.builders.jvmArtifactsHandlersStep
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontend2ClassicBackendConverter
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontend2IrConverter
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendFacade
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendOutputArtifact
import org.jetbrains.kotlin.test.frontend.classic.handlers.ClassicDiagnosticsHandler
import org.jetbrains.kotlin.test.frontend.classic.handlers.DeclarationsDumpHandler
import org.jetbrains.kotlin.test.frontend.classic.handlers.OldNewInferenceMetaInfoProcessor
import org.jetbrains.kotlin.test.frontend.fir.Fir2IrResultsConverter
import org.jetbrains.kotlin.test.frontend.fir.FirFrontendFacade
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.services.configuration.CommonEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.configuration.JvmEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.sourceProviders.AdditionalDiagnosticsSourceFilesProvider
import org.jetbrains.kotlin.test.services.sourceProviders.CoroutineHelpersSourceFilesProvider
import org.jetbrains.kotlin.test.services.configuration.ScriptingEnvironmentConfigurator

abstract class AbstractDiagnosticsTestWithJvmBackend<R : ResultingArtifact.FrontendOutput<R>, I : ResultingArtifact.BackendInput<I>, O : ResultingArtifact.MiddleendOutput<O>> :
    AbstractKotlinCompilerTest() {
    abstract val targetFrontend: FrontendKind<R>
    abstract val targetBackend: TargetBackend
    abstract val frontend: Constructor<FrontendFacade<R>>
    abstract val converter: Constructor<Frontend2BackendConverter<R, I>>
    abstract val middleendFacade: Constructor<IrMiddleendFacade<I, O>>
    abstract val backendFacade: Constructor<BackendFacade<O, BinaryArtifacts.Jvm>>

    override fun TestConfigurationBuilder.configuration() {
        globalDefaults {
            frontend = targetFrontend
            targetBackend = this@AbstractDiagnosticsTestWithJvmBackend.targetBackend
            targetPlatform = JvmPlatforms.defaultJvmPlatform
            dependencyKind = DependencyKind.Binary
        }

        defaultDirectives {
            +JvmEnvironmentConfigurationDirectives.USE_PSI_CLASS_FILES_READING
        }

        enableMetaInfoHandler()

        useConfigurators(
            ::CommonEnvironmentConfigurator,
            ::JvmEnvironmentConfigurator,
            ::ScriptingEnvironmentConfigurator,
        )

        useMetaInfoProcessors(::OldNewInferenceMetaInfoProcessor)
        useAdditionalSourceProviders(
            ::AdditionalDiagnosticsSourceFilesProvider,
            ::CoroutineHelpersSourceFilesProvider,
        )

        facadeStep(frontend)
        classicFrontendHandlersStep {
            useHandlers(
                ::DeclarationsDumpHandler,
                ::ClassicDiagnosticsHandler,
            )
        }

        facadeStep(converter)
        facadeStep(middleendFacade)
        facadeStep(backendFacade)

        jvmArtifactsHandlersStep {
            useHandlers(
                ::JvmBackendDiagnosticsHandler
            )
        }
    }
}

abstract class AbstractDiagnosticsTestWithOldJvmBackend : AbstractDiagnosticsTestWithJvmBackend<ClassicFrontendOutputArtifact, ClassicBackendInput, ClassicMiddleendOutput>() {
    override val targetFrontend: FrontendKind<ClassicFrontendOutputArtifact>
        get() = FrontendKinds.ClassicFrontend

    override val targetBackend: TargetBackend
        get() = TargetBackend.JVM_OLD

    override val frontend: Constructor<FrontendFacade<ClassicFrontendOutputArtifact>>
        get() = ::ClassicFrontendFacade

    override val converter: Constructor<Frontend2BackendConverter<ClassicFrontendOutputArtifact, ClassicBackendInput>>
        get() = ::ClassicFrontend2ClassicBackendConverter

    override val middleendFacade: Constructor<IrMiddleendFacade<ClassicBackendInput, ClassicMiddleendOutput>>
        get() = ::ClassicIrMiddleendFacade

    override val backendFacade: Constructor<BackendFacade<ClassicMiddleendOutput, BinaryArtifacts.Jvm>>
        get() = ::ClassicJvmBackendFacade
}

abstract class AbstractDiagnosticsTestWithJvmIrBackend : AbstractDiagnosticsTestWithJvmBackend<ClassicFrontendOutputArtifact, IrBackendInput, IrMiddleendOutput>() {
    override val targetFrontend: FrontendKind<ClassicFrontendOutputArtifact>
        get() = FrontendKinds.ClassicFrontend

    override val targetBackend: TargetBackend
        get() = TargetBackend.JVM_IR

    override val frontend: Constructor<FrontendFacade<ClassicFrontendOutputArtifact>>
        get() = ::ClassicFrontendFacade

    override val converter: Constructor<Frontend2BackendConverter<ClassicFrontendOutputArtifact, IrBackendInput>>
        get() = ::ClassicFrontend2IrConverter

    override val middleendFacade: Constructor<IrMiddleendFacade<IrBackendInput, IrMiddleendOutput>>
        get() = ::StandardIrMiddleendFacade

    override val backendFacade: Constructor<BackendFacade<IrMiddleendOutput, BinaryArtifacts.Jvm>>
        get() = ::JvmIrBackendFacade
}

abstract class AbstractFirDiagnosticsTestWithJvmIrBackend : AbstractDiagnosticsTestWithJvmBackend<FirOutputArtifact, IrBackendInput, IrMiddleendOutput>() {
    override val targetFrontend: FrontendKind<FirOutputArtifact>
        get() = FrontendKinds.FIR

    override val targetBackend: TargetBackend
        get() = TargetBackend.JVM_IR

    override val frontend: Constructor<FrontendFacade<FirOutputArtifact>>
        get() = ::FirFrontendFacade

    override val converter: Constructor<Frontend2BackendConverter<FirOutputArtifact, IrBackendInput>>
        get() = ::Fir2IrResultsConverter

    override val middleendFacade: Constructor<IrMiddleendFacade<IrBackendInput, IrMiddleendOutput>>
        get() = ::StandardIrMiddleendFacade

    override val backendFacade: Constructor<BackendFacade<IrMiddleendOutput, BinaryArtifacts.Jvm>>
        get() = ::JvmIrBackendFacade
}