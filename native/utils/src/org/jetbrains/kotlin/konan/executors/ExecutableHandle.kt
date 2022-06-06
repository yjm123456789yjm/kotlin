/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.executors

import java.io.Closeable
import kotlin.time.*

@OptIn(ExperimentalTime::class)
abstract class ExecutableHandle : Closeable {
    // Can only be set once.
    private var exitStatus: ExitStatus? = null

    val exitStatusOrNull by ::exitStatus

    fun join(timeout: Duration = Duration.INFINITE): ExitStatus = updateStatus { joinImpl(timeout) }

    fun terminate(): ExitStatus = updateStatus { terminateImpl() }

    final override fun close() {
        terminate()
    }

    protected abstract fun joinImpl(timeout: Duration): ExitStatus
    protected abstract fun terminateImpl(): ExitStatus.Terminated

    private inline fun updateStatus(f: () -> ExitStatus): ExitStatus {
        if (exitStatus != null) {
            return exitStatus!!
        }
        val status = f()
        if (exitStatus != null) {
            return exitStatus!!
        }
        exitStatus = status
        return status
    }
}