/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.llvm.clangArgsForRuntime
import org.jetbrains.kotlin.llvm.clangToExecutable
import org.jetbrains.kotlin.llvm.hostLlvmToolExecutable
import java.io.File
import javax.inject.Inject

abstract class ExecClang @Inject constructor(
        private val platformManager: PlatformManager,
    ) {

    @get:Inject
    protected abstract val fileOperations: FileOperations
    @get:Inject
    protected abstract val execOperations: ExecOperations

    private fun resolveExecutable(executableOrNull: String?): String {
        val executable = executableOrNull ?: "clang"

        if (listOf("clang", "clang++").contains(executable)) {
            return platformManager.hostLlvmToolExecutable(executable)
        } else {
            throw GradleException("unsupported clang executable: $executable")
        }
    }

    // Invoke clang with konan provided sysroots.
    fun execKonanClang(target: KonanTarget, action: Action<in ExecSpec>): ExecResult = execOperations.exec {
        action.execute(this)
        executable = resolveExecutable(executable)

        val hostPlatform = platformManager.hostPlatform
        environment["PATH"] = fileOperations.configurableFiles(hostPlatform.clang.clangPaths).asPath +
                File.pathSeparator + environment["PATH"]
        args = args + platformManager.clangArgsForRuntime(target)
    }

    /**
     * Execute Clang the way that produced object file is compatible with
     * the one that produced by Kotlin/Native for given [target]. It means:
     * 1. We pass flags that set sysroot.
     * 2. We call Clang from toolchain in case of Apple target.
     */
    fun execClangForCompilerTests(target: KonanTarget, action: Action<in ExecSpec>): ExecResult = execOperations.exec {
        action.execute(this)
        clangToExecutable(platformManager, target, executable ?: "clang")
        args(platformManager.platform(target).clang.clangArgs)
    }

    companion object {
        @JvmStatic
        fun create(project: Project) = create(project.objects, project.platformManager)

        fun create(objects: ObjectFactory, platformManager: PlatformManager): ExecClang = objects.newInstance(ExecClang::class.java, platformManager)
    }
}
