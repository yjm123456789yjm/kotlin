/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.bitcode

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.BasePlugin
import org.jetbrains.kotlin.GenerateCompilationDatabase
import org.jetbrains.kotlin.MergeCompilationDatabases
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.konan.target.SanitizerKind
import org.jetbrains.kotlin.konan.target.supportedSanitizers
import org.jetbrains.kotlin.testing.native.CompileNativeTest
import org.jetbrains.kotlin.testing.native.GoogleTestExtension
import org.jetbrains.kotlin.testing.native.RunNativeTest
import org.jetbrains.kotlin.testing.native.RuntimeTestingPlugin
import java.io.File
import javax.inject.Inject

/**
 * A plugin creating extensions to compile
 */
open class CompileToBitcodePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        extensions.create(EXTENSION_NAME, CompileToBitcodeExtension::class.java, target)
        Unit
    }

    companion object {
        const val EXTENSION_NAME = "bitcode"
    }
}

open class CompileToBitcodeExtension @Inject constructor(val project: Project) {

    private val targetList = with(project) {
        provider { (rootProject.project(":kotlin-native").property("targetList") as? List<*>)?.filterIsInstance<String>() ?: emptyList() } // TODO: Can we make it better?
    }

    private val allMainModulesTasks by lazy {
        val name = project.name.capitalize()
        targetList.get().associateBy(keySelector = { it }, valueTransform = {
            project.tasks.register("${it}$name")
        })
    }

    private val allTestsTasks by lazy {
        val name = project.name.capitalize()
        targetList.get().associateBy(keySelector = { it }, valueTransform = {
            project.tasks.register("${it}${name}Tests")
        })
    }

    private val compdbTasks by lazy {
        targetList.get().associateBy(keySelector = { it }, valueTransform = {
            val task = project.tasks.register("${it}${COMPILATION_DATABASE_TASK_NAME}", MergeCompilationDatabases::class.java)
            task.configure {
                outputFile = File(File(project.buildDir, it), "compile_commands.json")
            }
            task
        })
    }

    private fun compileToBitcode(
            folderName: String,
            targetName: String,
            sanitizer: SanitizerKind?,
            outputGroup: String,
            configurationBlock: CompileToBitcode.() -> Unit,
    ): LlvmLink {
        val compileTaskName = "${targetName}${folderName.snakeCaseToCamelCase().capitalize()}${suffixForSanitizer(sanitizer)}Bitcode"
        val compileTask = project.tasks.create(compileTaskName, CompileToBitcode::class.java, folderName, targetName, outputGroup).apply {
            this.sanitizer = sanitizer
            group = BasePlugin.BUILD_GROUP
            val sanitizerDescription = when (sanitizer) {
                null -> ""
                SanitizerKind.ADDRESS -> " with ASAN"
                SanitizerKind.THREAD -> " with TSAN"
            }
            description = "Compiles '$name' to bitcode for $targetName$sanitizerDescription"
            dependsOn(":kotlin-native:dependencies:update")
            configurationBlock()
        }

        // TODO: No need to create compdb tasks for different sanitizers.
        val compdbTaskName = "${targetName}${folderName.snakeCaseToCamelCase().capitalize()}${suffixForSanitizer(sanitizer)}CompilationDatabase"
        val compdbTask = project.tasks.create(compdbTaskName, GenerateCompilationDatabase::class.java, compileTask.target, compileTask.inputFiles, compileTask.executable, compileTask.compilerFlags, compileTask.objDir)
        compdbTasks[targetName]!!.configure {
            dependsOn(compdbTask)
            inputFiles.add(compdbTask.outputFile)
        }

        val linkTaskName = "${targetName}${folderName.snakeCaseToCamelCase().capitalize()}${suffixForSanitizer(sanitizer)}Link"
        val linkTask = project.tasks.create(linkTaskName, LlvmLink::class.java, folderName).apply {
            onlyIf {
                // TODO: Must be the same `onlyIf` applied in `configurationBlock`
                val state = compileTask.state
                state.executed || state.upToDate
            }
            targetDir = compileTask.targetDir
            inputFiles.addAll(compileTask.outputFiles)
            dependsOn(compileTask)
        }

        if (outputGroup == "main" && sanitizer == null) {
            allMainModulesTasks[targetName]!!.configure {
                dependsOn(linkTaskName)
            }
        }

        return linkTask
    }

    fun module(name: String, srcRoot: File = project.file("src/$name"), outputGroup: String = "main", configurationBlock: CompileToBitcode.() -> Unit = {}) {
        targetList.get().forEach { targetName ->
            val platformManager = project.rootProject.project(":kotlin-native").findProperty("platformManager") as PlatformManager
            val target = platformManager.targetByName(targetName)
            val sanitizers: List<SanitizerKind?> = target.supportedSanitizers() + listOf(null)
            sanitizers.forEach { sanitizer ->
                compileToBitcode(name, targetName, sanitizer, outputGroup) {
                    srcDirs = project.files(srcRoot.resolve("cpp"))
                    headersDirs = srcDirs + project.files(srcRoot.resolve("headers"))

                    configurationBlock()
                }
            }
        }
    }

    private fun createTestTask(
            testName: String,
            testedTaskNames: List<String>,
            target: String,
            sanitizer: SanitizerKind?,
    ) {
        val platformManager = project.project(":kotlin-native").findProperty("platformManager") as PlatformManager
        val googleTestExtension = project.extensions.getByName(RuntimeTestingPlugin.GOOGLE_TEST_EXTENSION_NAME) as GoogleTestExtension
        val testedTasks = testedTaskNames.map {
            val fullName = "${target}${it.snakeCaseToCamelCase().capitalize()}${suffixForSanitizer(sanitizer)}Link"
            project.tasks.getByName(fullName) as LlvmLink
        }
        val konanTarget = platformManager.targetByName(target)
        val testTasks = testedTaskNames.mapNotNull {
            val compileTaskName = "${target}${it.snakeCaseToCamelCase().capitalize()}${suffixForSanitizer(sanitizer)}Bitcode"
            val compileTask = project.tasks.getByName(compileTaskName) as CompileToBitcode
            val name = "${target}${it.snakeCaseToCamelCase().capitalize()}Test${suffixForSanitizer(sanitizer)}Link"
            val task = project.tasks.findByName(name) as? LlvmLink ?: compileToBitcode("${it}_test", target, sanitizer, "test") {
                srcDirs = compileTask.srcDirs
                headersDirs = compileTask.headersDirs + googleTestExtension.headersDirs
                excludeFiles = emptyList()
                includeFiles = listOf("**/*Test.cpp", "**/*TestSupport.cpp", "**/*Test.mm", "**/*TestSupport.mm")
                dependsOn("downloadGoogleTest")
                compilerArgs.addAll(compileTask.compilerArgs)
            }
            if (task.inputFiles.count() == 0) null
            else task
        }
        // TODO: Consider using sanitized versions.
        val testFrameworkTasks = listOf(project.tasks.getByName("${target}GoogletestLink") as LlvmLink, project.tasks.getByName("${target}GooglemockLink") as LlvmLink)

        val testSupportTask = project.tasks.getByName("${target}TestSupport${CompileToBitcodeExtension.suffixForSanitizer(sanitizer)}Link") as LlvmLink

        val mimallocEnabled = testedTaskNames.any { it.contains("mimalloc") }
        val compileTask = project.tasks.create(
                "${testName}Compile",
                CompileNativeTest::class.java,
                testName,
                konanTarget,
                testSupportTask.outFile,
                platformManager,
                mimallocEnabled,
        ).apply {
            val tasksToLink = (testTasks + testedTasks + testFrameworkTasks)
            this.sanitizer = sanitizer
            this.inputFiles.setFrom(tasksToLink.map { it.outFile })
            dependsOn(testSupportTask)
            dependsOn(tasksToLink)
        }

        val runTask = project.tasks.create(
                testName,
                RunNativeTest::class.java,
                testName,
                compileTask.outputFile,
        ).apply {
            this.sanitizer = sanitizer
            dependsOn(compileTask)
        }

        allTestsTasks[target]!!.configure {
            dependsOn(runTask)
        }
    }

    fun testsGroup(
            testTaskName: String,
            testedTaskNames: List<String>,
    ) {
        val platformManager = project.rootProject.project(":kotlin-native").findProperty("platformManager") as PlatformManager
        targetList.get().forEach { targetName ->
            val target = platformManager.targetByName(targetName)
            val sanitizers: List<SanitizerKind?> = target.supportedSanitizers() + listOf(null)
            sanitizers.forEach { sanitizer ->
                val suffix = CompileToBitcodeExtension.suffixForSanitizer(sanitizer)
                val name = targetName + testTaskName.snakeCaseToCamelCase().capitalize() + suffix
                createTestTask(name, testedTaskNames, targetName, sanitizer)
            }
        }
    }

    fun linkModules(name: String, modules: List<String>, outputGroup: String = "main") {
        val platformManager = project.rootProject.project(":kotlin-native").findProperty("platformManager") as PlatformManager
        targetList.get().forEach { targetName ->
            val target = platformManager.targetByName(targetName)
            val sanitizers: List<SanitizerKind?> = target.supportedSanitizers() + listOf(null)
            sanitizers.forEach { sanitizer ->
                val linkTaskName = "${targetName}${name.snakeCaseToCamelCase().capitalize()}${suffixForSanitizer(sanitizer)}Link"
                // TODO: Deduplicate with CompileToBitcode
                val sanitizerSuffix = when (sanitizer) {
                    null -> ""
                    SanitizerKind.ADDRESS -> "-asan"
                    SanitizerKind.THREAD -> "-tsan"
                }
                val targetDir = project.buildDir.resolve("bitcode/$outputGroup/$targetName$sanitizerSuffix")
                project.tasks.register(linkTaskName, LlvmLink::class.java, name).configure {
                    this.targetDir = targetDir
                    modules.forEach {
                        val moduleName = "${targetName}${it.snakeCaseToCamelCase().capitalize()}${suffixForSanitizer(sanitizer)}Link"
                        val task = project.tasks.findByName(moduleName) as LlvmLink
                        inputFiles.add(task.outFile)
                        dependsOn(task)
                    }
                }

                if (outputGroup == "main" && sanitizer == null) {
                    allMainModulesTasks[targetName]!!.configure {
                        dependsOn(linkTaskName)
                    }
                }
            }
        }
    }

    companion object {

        private const val COMPILATION_DATABASE_TASK_NAME = "CompilationDatabase"

        private fun String.snakeCaseToCamelCase() = split('_').joinToString(separator = "") { it.capitalize() }

        fun suffixForSanitizer(sanitizer: SanitizerKind?) = when (sanitizer) {
            null -> ""
            SanitizerKind.ADDRESS -> "_ASAN"
            SanitizerKind.THREAD -> "_TSAN"
        }

    }
}
