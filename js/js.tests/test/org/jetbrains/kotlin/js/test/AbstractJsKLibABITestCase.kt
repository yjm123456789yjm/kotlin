/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.test

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.createPhaseConfig
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.backend.js.*
import org.jetbrains.kotlin.ir.backend.js.codegen.JsGenerationGranularity
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrModuleToJsTransformerTmp
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.testOld.V8IrJsTestChecker
import org.jetbrains.kotlin.klib.AbstractKlibABITestCase
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractJsKLibABITestCase : AbstractKlibABITestCase() {

    override fun compileBinaryAndRun(project: Project, configuration: CompilerConfiguration, libraries: Collection<String>, mainModulePath: String, buildDir: File) {
        configuration.put(JSConfigurationKeys.PARTIAL_LINKAGE, true)
        val kLib = MainModule.Klib(mainModulePath)
        val moduleStructure = ModulesStructure(project, kLib, configuration, libraries, emptyList(), false, false, emptyMap())

        val phaseConfig = createPhaseConfig(jsPhases, K2JSCompilerArguments(), MessageCollector.NONE)

        val ir = compile(
            moduleStructure,
            phaseConfig,
            IrFactoryImpl,
            propertyLazyInitialization = true,
            granularity = JsGenerationGranularity.PER_MODULE,
            icCompatibleIr2Js = true
        )

        val transformer = IrModuleToJsTransformerTmp(
            ir.context,
            emptyList(),
            fullJs = true,
            dceJs = true,
            multiModule = true,
            relativeRequirePath = false
        )

        val compiledResult = transformer.generateModule(ir.allModules)

        val dceOutput = compiledResult.outputsAfterDce ?: error("No DCE output")

        val mainBinary = File(buildDir, MAIN_MODULE_NAME).toBinaryFile(MAIN_MODULE_NAME)
        mainBinary.writeText(dceOutput.jsCode)

        val binaries = ArrayList<File>(libraries.size)
        binaries.add(mainBinary)

        for ((name, code) in dceOutput.dependencies) {
            val depBinary = File(buildDir, name).toBinaryFile(name)
            depBinary.writeText(code.jsCode)
            binaries.add(depBinary)
        }

        executeAndCheckBinaries(MAIN_MODULE_NAME, binaries)
    }

    private fun executeAndCheckBinaries(mainModuleName: String, dependencies: Collection<File>) {
        val checker = V8IrJsTestChecker

        val filePaths = dependencies.map { it.canonicalPath }
        checker.check(filePaths, mainModuleName, null, "box", "OK", withModuleSystem = false)
    }

    override fun buildKlibImpl(
        project: Project,
        configuration: CompilerConfiguration,
        moduleName: String,
        sources: Collection<KtFile>,
        dependencies: Collection<String>,
        outputFile: File
    ) {
        val sourceModule = prepareAnalyzedSourceModule(
            project,
            sources.toList(),
            configuration,
            dependencies.toList(),
            emptyList(), // TODO
            AnalyzerWithCompilerReport(configuration)
        )

        generateKLib(sourceModule, IrFactoryImpl, outputFile.canonicalPath, nopack = false, jsOutputName = moduleName)
    }

    override fun stdlibPath(): String = "libraries/stdlib/js-ir/build/libs/kotlin-stdlib-js-ir-js-1.6.255-SNAPSHOT.klib"
}