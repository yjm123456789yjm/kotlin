/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.llvm

import org.gradle.api.GradleException
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.PlatformManager

fun PlatformManager.hostLlvmToolExecutable(tool: String) = "${hostPlatform.absoluteLlvmHome}/bin/$tool"

fun ExecSpec.hostLlvmTool(platformManager: PlatformManager, tool: String) {
    executable = platformManager.hostLlvmToolExecutable(tool)
}

// TODO: This is copied from `BitcodeCompiler`. Consider sharing the code instead.
fun PlatformManager.toolchainLlvmToolExecutable(target: KonanTarget, tool: String) = if (target.family.isAppleFamily) {
    "${platform(target).absoluteTargetToolchain}/usr/bin/$tool"
} else {
    "${platform(target).absoluteTargetToolchain}/bin/$tool"
}

fun ExecSpec.toolchainLlvmTool(platformManager: PlatformManager, target: KonanTarget, tool: String) {
    executable = platformManager.toolchainLlvmToolExecutable(target, tool)
}

fun PlatformManager.clangArgsForRuntime(target: KonanTarget) = platform(target).clang.clangArgsForKonanSources.asList()

fun ExecSpec.clangToExecutable(platformManager: PlatformManager, target: KonanTarget, tool: String) {
    if (!listOf("clang", "clang++").contains(tool)) {
        throw GradleException("unsupported clang executable: $tool")
    }
    if (target.family.isAppleFamily) {
        toolchainLlvmTool(platformManager, target, tool)
    } else {
        hostLlvmTool(platformManager, tool)
    }
}