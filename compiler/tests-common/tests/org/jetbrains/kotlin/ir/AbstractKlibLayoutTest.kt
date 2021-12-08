/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.backend.common.serialization.codedInputStream
import org.jetbrains.kotlin.backend.common.serialization.proto.IrFile
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.codegen.CodegenTestCase
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.incremental.md5
import org.jetbrains.kotlin.ir.backend.js.ModulesStructure
import org.jetbrains.kotlin.ir.backend.js.generateKLib
import org.jetbrains.kotlin.ir.backend.js.jsResolveLibraries
import org.jetbrains.kotlin.ir.backend.js.prepareAnalyzedSourceModule
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.protobuf.ExtensionRegistryLite
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.util.DummyLogger
import java.io.File


private const val MODULE_NAME = "M"

abstract class AbstractKlibLayoutTest : CodegenTestCase() {

    private fun loadKtFiles(directory: File): List<KtFile> {
        val psiManager = PsiManager.getInstance(myEnvironment.project)
        val fileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)

        val vDirectory = fileSystem.findFileByPath(directory.canonicalPath) ?: error("File not found: $directory")
        return psiManager.findDirectory(vDirectory)?.files?.map { it as KtFile } ?: error("Cannot load KtFiles")
    }

    private val runtimeKlibPath = "libraries/stdlib/js-ir/build/classes/kotlin/js/main"

    private fun analyseKtFiles(configuration: CompilerConfiguration, ktFiles: List<KtFile>): ModulesStructure {
        return prepareAnalyzedSourceModule(
            myEnvironment.project,
            ktFiles,
            configuration,
            listOf(runtimeKlibPath),
            emptyList(),
            AnalyzerWithCompilerReport(configuration),
        )
    }

    private fun produceKlib(module: ModulesStructure, destination: File) {
        generateKLib(module, irFactory = IrFactoryImpl, outputKlibPath = destination.path, nopack = false, jsOutputName = MODULE_NAME)
    }


    private fun setupEnvironment(configuration: CompilerConfiguration) {
        myEnvironment = KotlinCoreEnvironment.createForTests(testRootDisposable, configuration, EnvironmentConfigFiles.JS_CONFIG_FILES)
    }


    override fun doMultiFileTest(wholeFile: File, files: List<TestFile>) {
        val dirAPath = kotlin.io.path.createTempDirectory()
        val dirBPath = kotlin.io.path.createTempDirectory()
        val dummyPath = kotlin.io.path.createTempDirectory()


        val dirAFile = dirAPath.toFile().also { assert(it.isDirectory) }
        val dirBFile = dirBPath.toFile().also { assert(it.isDirectory) }
        val dummyDir = dummyPath.toFile()

        try {
            for (testFile in files) {
                val testFileA = File(dirAFile, testFile.name).also { it.parentFile.let { p -> if (!p.exists()) p.mkdirs() } }
                val testFileB = File(dirBFile, testFile.name).also { it.parentFile.let { p -> if (!p.exists()) p.mkdirs() } }

                testFileA.writeText(testFile.content)
                testFileB.writeText(testFile.content)
            }

            val configuration = CompilerConfiguration()
            configuration.put(CommonConfigurationKeys.MODULE_NAME, MODULE_NAME)
            setupEnvironment(configuration)

            val ktFilesA = loadKtFiles(dirAFile)
            val ktFilesB = loadKtFiles(dirBFile)

            val moduleA = analyseKtFiles(configuration, ktFilesA)
            val moduleB = analyseKtFiles(configuration, ktFilesB)

            val moduleAAbsolute = File(dirAFile, "${MODULE_NAME}_A.klib")
            val moduleBAbsolute = File(dirBFile, "${MODULE_NAME}_A.klib")

            produceKlib(moduleA, moduleAAbsolute)
            produceKlib(moduleB, moduleBAbsolute)

            configuration.put(CommonConfigurationKeys.KLIB_RELATIVE_PATH_BASES, listOf(dirAFile.canonicalPath, dirBFile.canonicalPath))

            val moduleARelative = File(dirAFile, "${MODULE_NAME}_R.klib")
            val moduleBRelative = File(dirBFile, "${MODULE_NAME}_R.klib")

            produceKlib(moduleA, moduleARelative)
            produceKlib(moduleB, moduleBRelative)

            checkPaths(dirAFile, dirBFile, moduleAAbsolute, moduleBAbsolute, moduleARelative, moduleBRelative, false)

            configuration.put(CommonConfigurationKeys.KLIB_RELATIVE_PATH_BASES, listOf(dummyDir.canonicalPath))

            moduleAAbsolute.delete()
            moduleBAbsolute.delete()

            produceKlib(moduleA, moduleAAbsolute)
            produceKlib(moduleB, moduleBAbsolute)

            checkPaths(dirAFile, dirBFile, moduleAAbsolute, moduleBAbsolute, moduleARelative, moduleBRelative, false)

            moduleAAbsolute.delete()
            moduleBAbsolute.delete()

            configuration.put(CommonConfigurationKeys.KLIB_RELATIVE_PATH_BASES, emptyList())
            configuration.put(CommonConfigurationKeys.KLIB_NORMALIZE_ABSOLUTE_PATH, true)

            produceKlib(moduleA, moduleAAbsolute)
            produceKlib(moduleB, moduleBAbsolute)

            checkPaths(dirAFile, dirBFile, moduleAAbsolute, moduleBAbsolute, moduleARelative, moduleBRelative, true)
        } finally {
            dirAFile.deleteRecursively()
            dirBFile.deleteRecursively()
            dummyDir.deleteRecursively()
        }
    }

    private fun File.md5(): Long = readBytes().md5()

    private fun checkPaths(
        dirAFile: File,
        dirBFile: File,
        moduleAAbsolute: File,
        moduleBAbsolute: File,
        moduleARelative: File,
        moduleBRelative: File,
        denormalizeAbsolutePath: Boolean
    ) {
        val rA_md5 = moduleARelative.md5()
        val rB_md5 = moduleBRelative.md5()

        assertEquals(rA_md5, rB_md5)

        val moduleAAbsolutePaths = moduleAAbsolute.loadKlibFilePaths(denormalize = denormalizeAbsolutePath)
        val moduleBAbsolutePaths = moduleBAbsolute.loadKlibFilePaths(denormalize = denormalizeAbsolutePath)

        val dirACCanonicalPaths = dirAFile.listFiles { _, name -> name.endsWith(".kt") }!!.map { it.canonicalPath }
        val dirBCCanonicalPaths = dirBFile.listFiles { _, name -> name.endsWith(".kt") }!!.map { it.canonicalPath }

        assertSameElements(moduleAAbsolutePaths, dirACCanonicalPaths)
        assertSameElements(moduleBAbsolutePaths, dirBCCanonicalPaths)

        val moduleARelativePaths = moduleARelative.loadKlibFilePaths(denormalize = true)
        val moduleBRelativePaths = moduleBRelative.loadKlibFilePaths(denormalize = true)

        assertSameElements(moduleARelativePaths, moduleBRelativePaths)

        val aPathsA2R = moduleAAbsolutePaths.map { File(it).relativeTo(dirAFile).path }
        val bPathsA2R = moduleBAbsolutePaths.map { File(it).relativeTo(dirBFile).path }

        assertSameElements(aPathsA2R, moduleARelativePaths)
        assertSameElements(bPathsA2R, moduleBRelativePaths)
        assertSameElements(aPathsA2R, bPathsA2R)
    }

    private fun File.loadKlibFilePaths(denormalize: Boolean): List<String> {
        val libs = jsResolveLibraries(listOf(runtimeKlibPath, canonicalPath), emptyList(), DummyLogger).getFullList()
        val lib = libs.last()
        val fileSize = lib.fileCount()
        val extReg = ExtensionRegistryLite.newInstance()

        val result = ArrayList<String>(fileSize)

        for (i in 0 until fileSize) {
            val fileStream = lib.file(i).codedInputStream
            val fileProto = IrFile.parseFrom(fileStream, extReg)
            val fileName = fileProto.fileEntry.name

            if (denormalize) {
                result.add(fileName.replace("/", File.separator))
            } else {
                result.add(fileName)
            }
        }

        return result
    }

}