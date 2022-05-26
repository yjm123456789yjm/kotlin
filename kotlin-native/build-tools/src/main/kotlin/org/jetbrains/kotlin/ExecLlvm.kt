/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.llvm.hostLlvmTool

fun execLlvmUtility(project: Project, utility: String, action: Action<in ExecSpec>): ExecResult = project.exec {
    action.execute(this)
    hostLlvmTool(project.platformManager, utility)
}

fun execLlvmUtility(project: Project, utility: String, closure: Closure<in ExecSpec>) =
        execLlvmUtility(project, utility) { project.configure(this, closure) }
