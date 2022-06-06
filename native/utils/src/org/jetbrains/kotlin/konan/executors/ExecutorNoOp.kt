/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.executors

import kotlin.time.*

@OptIn(ExperimentalTime::class)
class ExecutorNoOp : Executor {
    override fun execute(executable: ExecutableSpec): ExecutableHandle {
        return object : ExecutableHandle() {
            override fun joinImpl(): ExitStatus {
                return ExitStatus.Success(Duration.ZERO)
            }

            override fun terminateImpl(): ExitStatus.Terminated {
                return ExitStatus.Terminated(Duration.ZERO)
            }
        }
    }
}