/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.runners.codegen

import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.classic.ClassicBackendInput
import org.jetbrains.kotlin.test.backend.classic.ClassicMiddleendOutput
import org.jetbrains.kotlin.test.backend.classic.ClassicJvmBackendFacade
import org.jetbrains.kotlin.test.backend.ir.*
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontend2ClassicBackendConverter
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontend2IrConverter
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendFacade
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.Fir2IrResultsConverter
import org.jetbrains.kotlin.test.frontend.fir.FirFrontendFacade
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.*

open class AbstractIrSteppingTest : AbstractSteppingTestBase<ClassicFrontendOutputArtifact, IrBackendInput, IrMiddleendOutput>(
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

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.configureDumpHandlersForCodegenTest()
    }
}

open class AbstractSteppingTest : AbstractSteppingTestBase<ClassicFrontendOutputArtifact, ClassicBackendInput, ClassicMiddleendOutput>(
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

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.configureDumpHandlersForCodegenTest()
    }
}

open class AbstractFirSteppingTest : AbstractSteppingTestBase<FirOutputArtifact, IrBackendInput, IrMiddleendOutput>(
    FrontendKinds.FIR,
    TargetBackend.JVM_IR
) {
    override val frontendFacade: Constructor<FrontendFacade<FirOutputArtifact>>
        get() = ::FirFrontendFacade

    override val frontendToBackendConverter: Constructor<Frontend2BackendConverter<FirOutputArtifact, IrBackendInput>>
        get() = ::Fir2IrResultsConverter

    override val middleendFacade: Constructor<MiddleendFacade<IrBackendInput, IrMiddleendOutput>>
        get() = ::StandardIrMiddleendFacade

    override val backendFacade: Constructor<BackendFacade<IrMiddleendOutput, BinaryArtifacts.Jvm>>
        get() = ::JvmIrBackendFacade

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.configureDumpHandlersForCodegenTest()
    }
}
