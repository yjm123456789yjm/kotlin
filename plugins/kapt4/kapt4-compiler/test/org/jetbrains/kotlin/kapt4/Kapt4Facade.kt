/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import org.jetbrains.kotlin.codegen.ClassBuilderMode
import org.jetbrains.kotlin.codegen.GenerationUtils
import org.jetbrains.kotlin.codegen.OriginCollectingClassBuilderFactory
import org.jetbrains.kotlin.kapt3.KaptContextForStubGeneration
import org.jetbrains.kotlin.kapt3.test.KaptMessageCollectorProvider
import org.jetbrains.kotlin.kapt3.util.MessageCollectorBackedKaptLogger
import org.jetbrains.kotlin.test.model.*
import org.jetbrains.kotlin.test.services.*

class Kapt4Facade(private val testServices: TestServices) :
    AbstractTestFacade<ResultingArtifact.Source, KaptContextBinaryArtifact>() {
    override val inputKind: TestArtifactKind<ResultingArtifact.Source>
        get() = SourcesKind
    override val outputKind: TestArtifactKind<KaptContextBinaryArtifact>
        get() = KaptContextBinaryArtifact.Kind

    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::KaptMessageCollectorProvider))

    override fun transform(module: TestModule, inputArtifact: ResultingArtifact.Source): KaptContextBinaryArtifact {
        val configurationProvider = testServices.compilerConfigurationProvider
        val project = configurationProvider.getProject(module)
        val ktFiles = testServices.sourceFileProvider.getKtFilesForSourceFiles(module.files, project).values.toList()
        Kapt4Main.run(configurationProvider.getCompilerConfiguration(module))
        TODO()
    }

    override fun shouldRunAnalysis(module: TestModule): Boolean {
        return true // TODO
    }
}

class KaptContextBinaryArtifact() : ResultingArtifact.Binary<KaptContextBinaryArtifact>() {
    object Kind : BinaryKind<KaptContextBinaryArtifact>("KaptArtifact")

    override val kind: BinaryKind<KaptContextBinaryArtifact>
        get() = Kind
}

