/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import com.sun.tools.javac.tree.JCTree
import com.sun.tools.javac.util.List
import org.jetbrains.kotlin.kapt3.base.parseJavaFiles
import org.jetbrains.kotlin.test.model.AnalysisHandler
import org.jetbrains.kotlin.test.model.TestArtifactKind
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.getRealJavaFiles
import org.jetbrains.kotlin.test.services.sourceFileProvider
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import java.io.File

class Kapt4Handler(testServices: TestServices) : AnalysisHandler<Kapt4ContextBinaryArtifact>(
    testServices,
    failureDisablesNextSteps = true,
    doNotRunIfThereWerePreviousFailures = true
) {
    override val artifactKind: TestArtifactKind<Kapt4ContextBinaryArtifact>
        get() = Kapt4ContextBinaryArtifact.Kind

    override fun processModule(module: TestModule, info: Kapt4ContextBinaryArtifact) {
        getJavaFiles(info, module)
    }

    private fun getJavaFiles(
        info: Kapt4ContextBinaryArtifact,
        module: TestModule
    ): List<JCTree.JCCompilationUnit> {
        val (kaptContext, kaptStubs) = info
        val convertedFiles = kaptStubs.mapIndexed { index, stub ->
            val sourceFile = createTempJavaFile("stub$index.java", "")//stub.file.prettyPrint(kaptContext.context))
            stub.writeMetadataIfNeeded(forSource = sourceFile)
            sourceFile
        }

        val javaFiles = testServices.sourceFileProvider.getRealJavaFiles(module)
        val allJavaFiles = javaFiles + convertedFiles

        // A workaround needed for Javac to parse files correctly even if errors were already reported
        // If nerrors > 0, "parseFiles()" returns the empty list
        val oldErrorCount = kaptContext.compiler.log.nerrors
        kaptContext.compiler.log.nerrors = 0

        try {
            val parsedJavaFiles = kaptContext.parseJavaFiles(allJavaFiles)

            for (tree in parsedJavaFiles) {
                val actualFile = File(tree.sourceFile.toUri())

                // By default, JavaFileObject.getName() returns the absolute path to the file.
                // In our test, such a path will be temporary, so the comparison against it will lead to flaky tests.
                tree.sourcefile = KaptJavaFileObject(tree, tree.defs.firstIsInstance(), actualFile)
            }

            return parsedJavaFiles
        } finally {
            kaptContext.compiler.log.nerrors = oldErrorCount
        }
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {
        TODO("Not yet implemented")
    }

    private fun createTempJavaFile(name: String, text: String): File {
        return testServices.sourceFileProvider.javaSourceDirectory.resolve(name).also {
            it.writeText(text)
        }
    }

}
