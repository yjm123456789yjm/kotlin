/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.bitcode

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

interface LlvmLinkParameters : WorkParameters {
    var llvmLinkArgs: List<String>
    var llvmDir: File
}

abstract class LlvmLinkJob : WorkAction<LlvmLinkParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun execute() {
        with(parameters) {
            execOperations.exec {
                executable = "${llvmDir.absolutePath}/bin/llvm-link"
                args = llvmLinkArgs
            }
        }
    }
}

abstract class LlvmLink @Inject constructor(@Input val moduleName: String) : DefaultTask() {

    @Input
    val linkerArgs = mutableListOf<String>()

    @get:Internal
    abstract var targetDir: File

    @SkipWhenEmpty
    @InputFiles
    val inputFiles = mutableListOf<File>()

    @get:OutputFile
    val outFile: File
        get() = File(targetDir, "${moduleName}.bc")

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun link() {
        val workQueue = workerExecutor.noIsolation()

        val parameters = { it: LlvmLinkParameters ->
            it.llvmLinkArgs = listOf("-o", outFile.absolutePath) + linkerArgs + inputFiles.map { it.absolutePath }
            it.llvmDir = project.file(project.findProperty("llvmDir")!!)
        }

        workQueue.submit(LlvmLinkJob::class.java, parameters)
    }
}
