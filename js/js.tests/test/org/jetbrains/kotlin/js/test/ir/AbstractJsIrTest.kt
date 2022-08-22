/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.test.ir

import org.jetbrains.kotlin.js.test.AbstractJsBlackBoxCodegenTestBase
import org.jetbrains.kotlin.js.test.converters.FirJsKlibBackendFacade
import org.jetbrains.kotlin.js.test.converters.JsIrBackendFacade
import org.jetbrains.kotlin.js.test.converters.JsKlibBackendFacade
import org.jetbrains.kotlin.js.test.converters.incremental.RecompileModuleJsIrBackendFacade
import org.jetbrains.kotlin.js.test.handlers.JsDebugRunner
import org.jetbrains.kotlin.js.test.handlers.JsDtsHandler
import org.jetbrains.kotlin.js.test.handlers.JsIrRecompiledArtifactsIdentityHandler
import org.jetbrains.kotlin.js.test.handlers.JsLineNumberHandler
import org.jetbrains.kotlin.parsing.parseBoolean
import org.jetbrains.kotlin.test.Constructor
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.*
import org.jetbrains.kotlin.test.directives.JsEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontend2IrConverter
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendFacade
import org.jetbrains.kotlin.test.frontend.classic.ClassicFrontendOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.Fir2IrJsResultsConverter
import org.jetbrains.kotlin.test.frontend.fir.FirFrontendFacade
import org.jetbrains.kotlin.test.frontend.fir.FirMetaInfoDiffSuppressor
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.handlers.*
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.runners.codegen.commonFirHandlersForCodegenTest
import org.jetbrains.kotlin.test.services.configuration.JsEnvironmentConfigurator
import java.lang.Boolean.getBoolean

abstract class AbstractJsIrTest(
    pathToTestDir: String,
    testGroupOutputDirPrefix: String,
) : AbstractJsBlackBoxCodegenTestBase<ClassicFrontendOutputArtifact, IrBackendInput, BinaryArtifacts.KLib>(
    FrontendKinds.ClassicFrontend, TargetBackend.JS_IR, pathToTestDir, testGroupOutputDirPrefix, skipMinification = true
) {
    override val frontendFacade: Constructor<FrontendFacade<ClassicFrontendOutputArtifact>>
        get() = ::ClassicFrontendFacade

    override val frontendToBackendConverter: Constructor<Frontend2BackendConverter<ClassicFrontendOutputArtifact, IrBackendInput>>
        get() = ::ClassicFrontend2IrConverter

    override val backendFacade: Constructor<BackendFacade<IrBackendInput, BinaryArtifacts.KLib>>
        get() = ::JsKlibBackendFacade

    override val afterBackendFacade: Constructor<AbstractTestFacade<BinaryArtifacts.KLib, BinaryArtifacts.Js>>?
        get() = ::JsIrBackendFacade

    override val recompileFacade: Constructor<AbstractTestFacade<BinaryArtifacts.Js, BinaryArtifacts.Js>>
        get() = { RecompileModuleJsIrBackendFacade(it) }

    private fun getBoolean(s: String, default: Boolean) = System.getProperty(s)?.let { parseBoolean(it) } ?: default

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            defaultDirectives {
                val runIc = getBoolean("kotlin.js.ir.icMode")
                if (runIc) +JsEnvironmentConfigurationDirectives.RUN_IC
                if (getBoolean("kotlin.js.ir.klibMainModule")) +JsEnvironmentConfigurationDirectives.KLIB_MAIN_MODULE
                if (getBoolean("kotlin.js.ir.perModule", true)) +JsEnvironmentConfigurationDirectives.PER_MODULE
                if (getBoolean("kotlin.js.ir.dce", true)) +JsEnvironmentConfigurationDirectives.RUN_IR_DCE
                if (getBoolean("kotlin.js.ir.newIr2Js", true)) +JsEnvironmentConfigurationDirectives.RUN_NEW_IR_2_JS
                -JsEnvironmentConfigurationDirectives.GENERATE_NODE_JS_RUNNER
            }

            configureJsArtifactsHandlersStep {
                useHandlers(
                    ::JsIrRecompiledArtifactsIdentityHandler,
                )
            }

            forTestsMatching("${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/box/closure/inlineAnonymousFunctions/*") {
                defaultDirectives {
                    +JsEnvironmentConfigurationDirectives.GENERATE_INLINE_ANONYMOUS_FUNCTIONS
                }
            }
        }
    }
}

open class AbstractIrBoxJsTest : AbstractJsIrTest(
    pathToTestDir = "${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/box/",
    testGroupOutputDirPrefix = "irBox/"
)

open class AbstractIrJsCodegenBoxTest : AbstractJsIrTest(
    pathToTestDir = "compiler/testData/codegen/box/",
    testGroupOutputDirPrefix = "codegen/irBox/"
)

open class AbstractIrJsCodegenBoxErrorTest : AbstractJsIrTest(
    pathToTestDir = "compiler/testData/codegen/boxError/",
    testGroupOutputDirPrefix = "codegen/irBoxError/"
)

open class AbstractIrJsCodegenInlineTest : AbstractJsIrTest(
    pathToTestDir = "compiler/testData/codegen/boxInline/",
    testGroupOutputDirPrefix = "codegen/irBoxInline/"
)

open class AbstractIrJsTypeScriptExportTest : AbstractJsIrTest(
    pathToTestDir = "${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/typescript-export/",
    testGroupOutputDirPrefix = "typescript-export/"
) {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        configureIrJsTypeScriptExportTest(builder)
    }
}

private fun configureIrJsTypeScriptExportTest(builder: TestConfigurationBuilder) {
    with(builder) {
        defaultDirectives {
            +JsEnvironmentConfigurationDirectives.GENERATE_DTS
            if (getBoolean("kotlin.js.updateReferenceDtsFiles")) +JsEnvironmentConfigurationDirectives.UPDATE_REFERENCE_DTS_FILES
        }

        configureJsArtifactsHandlersStep {
            useHandlers(
                ::JsDtsHandler
            )
        }
    }
}

open class AbstractJsIrLineNumberTest : AbstractJsIrTest(
    pathToTestDir = "${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/lineNumbers/",
    testGroupOutputDirPrefix = "irLineNumbers/"
) {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        configureJsIrLineNumberTest(builder)
    }
}

private fun configureJsIrLineNumberTest(builder: TestConfigurationBuilder) {
    with(builder) {
        defaultDirectives {
            +JsEnvironmentConfigurationDirectives.KJS_WITH_FULL_RUNTIME
            +JsEnvironmentConfigurationDirectives.NO_COMMON_FILES
            -JsEnvironmentConfigurationDirectives.GENERATE_NODE_JS_RUNNER
            JsEnvironmentConfigurationDirectives.DONT_RUN_GENERATED_CODE.with(listOf("JS", "JS_IR", "JS_IR_ES6"))
        }
        configureJsArtifactsHandlersStep {
            useHandlers(::JsLineNumberHandler)
        }
    }
}

open class AbstractIrJsSteppingTest : AbstractJsIrTest(
    pathToTestDir = "compiler/testData/debug/stepping/",
    testGroupOutputDirPrefix = "debug/stepping/"
) {
    override fun TestConfigurationBuilder.configuration() {
        commonConfigurationForJsBlackBoxCodegenTest()
        configurationForIrJsSteppingTest()
    }
}

private fun TestConfigurationBuilder.configurationForIrJsSteppingTest() {
    defaultDirectives {
        +JsEnvironmentConfigurationDirectives.NO_COMMON_FILES
    }
    jsArtifactsHandlersStep {
        useHandlers(::JsDebugRunner)
    }
}

open class AbstractIrCodegenWasmJsInteropJsTest : AbstractJsIrTest(
    pathToTestDir = "compiler/testData/codegen/wasmJsInterop",
    testGroupOutputDirPrefix = "codegen/wasmJsInteropJs"
)

open class AbstractFirJsTest(
    pathToTestDir: String = "${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/box/",
    testGroupOutputDirPrefix: String = "firBox/",
) : AbstractJsBlackBoxCodegenTestBase<FirOutputArtifact, IrBackendInput, BinaryArtifacts.KLib>(
    FrontendKinds.FIR, TargetBackend.JS_IR, pathToTestDir, testGroupOutputDirPrefix, skipMinification = true
) {
    override val frontendFacade: Constructor<FrontendFacade<FirOutputArtifact>>
        get() = ::FirFrontendFacade

    override val frontendToBackendConverter: Constructor<Frontend2BackendConverter<FirOutputArtifact, IrBackendInput>>
        get() = ::Fir2IrJsResultsConverter

    override val backendFacade: Constructor<BackendFacade<IrBackendInput, BinaryArtifacts.KLib>>
        get() = ::FirJsKlibBackendFacade

    override val afterBackendFacade: Constructor<AbstractTestFacade<BinaryArtifacts.KLib, BinaryArtifacts.Js>>?
        get() = ::JsIrBackendFacade

    override val recompileFacade: Constructor<AbstractTestFacade<BinaryArtifacts.Js, BinaryArtifacts.Js>>
        get() = { RecompileModuleJsIrBackendFacade(it) }

    private fun getBoolean(s: String, default: Boolean) = System.getProperty(s)?.let { parseBoolean(it) } ?: default

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with (builder) {
            defaultDirectives {
                val runIc = getBoolean("kotlin.js.ir.icMode")
                if (runIc) +JsEnvironmentConfigurationDirectives.RUN_IC
                if (getBoolean("kotlin.js.ir.klibMainModule")) +JsEnvironmentConfigurationDirectives.KLIB_MAIN_MODULE
                if (getBoolean("kotlin.js.ir.perModule", true)) +JsEnvironmentConfigurationDirectives.PER_MODULE
                if (getBoolean("kotlin.js.ir.dce", true)) +JsEnvironmentConfigurationDirectives.RUN_IR_DCE
                if (getBoolean("kotlin.js.ir.newIr2Js", true)) +JsEnvironmentConfigurationDirectives.RUN_NEW_IR_2_JS
                +LanguageSettingsDirectives.ALLOW_KOTLIN_PACKAGE
                -JsEnvironmentConfigurationDirectives.GENERATE_NODE_JS_RUNNER
            }

            firHandlersStep {
                useHandlers(
                    ::FirDumpHandler,
                    ::FirCfgDumpHandler,
                    ::FirCfgConsistencyHandler,
                    ::FirNoImplicitTypesHandler,
                )
            }

            configureJsArtifactsHandlersStep {
                useHandlers(
                    ::JsIrRecompiledArtifactsIdentityHandler,
                )
            }

            forTestsMatching("${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/box/closure/inlineAnonymousFunctions/*") {
                defaultDirectives {
                    +JsEnvironmentConfigurationDirectives.GENERATE_INLINE_ANONYMOUS_FUNCTIONS
                }
            }
        }
    }
}

open class AbstractFirBoxJsTest : AbstractFirJsTest(
    pathToTestDir = "${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/box/",
    testGroupOutputDirPrefix = "irBox/"
)

open class AbstractFirJsCodegenBoxTest : AbstractFirJsTest(
    pathToTestDir = "compiler/testData/codegen/box/",
    testGroupOutputDirPrefix = "codegen/irBox/"
) {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.configureFirHandlersStep {
            commonFirHandlersForCodegenTest()
        }

        builder.useAfterAnalysisCheckers(
            ::FirMetaInfoDiffSuppressor
        )
    }
}

open class AbstractFirJsCodegenBoxErrorTest : AbstractFirJsTest(
    pathToTestDir = "compiler/testData/codegen/boxError/",
    testGroupOutputDirPrefix = "codegen/irBoxError/"
)

open class AbstractFirJsCodegenInlineTest : AbstractFirJsTest(
    pathToTestDir = "compiler/testData/codegen/boxInline/",
    testGroupOutputDirPrefix = "codegen/irBoxInline/"
)

// TODO: implement method order independent comparison to reuse testdata, disabled for now
//open class AbstractFirJsTypeScriptExportTest : AbstractFirJsTest(
//    pathToTestDir = "${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/typescript-export/",
//    testGroupOutputDirPrefix = "typescript-export/"
//) {
//    override fun configure(builder: TestConfigurationBuilder) {
//        super.configure(builder)
//        configureIrJsTypeScriptExportTest(builder)
//    }
//}

// TODO: implement separate expectations for FIR/JS to reuse testdata, disabled for now
//open class AbstractJsFirLineNumberTest : AbstractFirJsTest(
//    pathToTestDir = "${JsEnvironmentConfigurator.TEST_DATA_DIR_PATH}/lineNumbers/",
//    testGroupOutputDirPrefix = "irLineNumbers/"
//) {
//    override fun configure(builder: TestConfigurationBuilder) {
//        super.configure(builder)
//        configureJsIrLineNumberTest(builder)
//    }
//}

// TODO: implement separate expectations for FIR/JS to reuse testdata, disabled for now
//open class AbstractFirJsSteppingTest : AbstractFirJsTest(
//    pathToTestDir = "compiler/testData/debug/stepping/",
//    testGroupOutputDirPrefix = "debug/stepping/"
//) {
//    override fun TestConfigurationBuilder.configuration() {
//        commonConfigurationForJsBlackBoxCodegenTest()
//        configurationForIrJsSteppingTest()
//    }
//}

open class AbstractFirCodegenWasmJsInteropJsTest : AbstractFirJsTest(
    pathToTestDir = "compiler/testData/codegen/wasmJsInterop",
    testGroupOutputDirPrefix = "codegen/wasmJsInteropJs"
)
