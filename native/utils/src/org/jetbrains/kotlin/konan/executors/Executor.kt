/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.executors

import kotlin.time.*

/**
 * Interface for executing across different targets.
 */
@ExperimentalTime
interface Executor {
    /**
     * Schedule execution of [executable].
     *
     * @param executable parameters for execution
     * @return handle to wait until execution completes
     */
    fun execute(executable: ExecutableSpec): ExecutableHandle

    /**
     * Execute [executable] and wait until it completes.
     *
     * If execution takes more than [timeout], terminates [executable].
     *
     * @param executable parameters for execution
     * @param timeout how long to wait
     */
    fun executeAndWait(executable: ExecutableSpec, timeout: Duration = Duration.INFINITE): ExitStatus = execute(executable).use {
        it.join(timeout)
    }
}