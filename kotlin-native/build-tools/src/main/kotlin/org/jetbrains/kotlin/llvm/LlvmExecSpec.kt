/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.llvm

import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.konan.target.PlatformManager

fun PlatformManager.hostLlvmToolExecutable(tool: String) = "${hostPlatform.absoluteLlvmHome}/bin/$tool"

fun ExecSpec.hostLlvmTool(platformManager: PlatformManager, tool: String) {
    executable = platformManager.hostLlvmToolExecutable(tool)
}