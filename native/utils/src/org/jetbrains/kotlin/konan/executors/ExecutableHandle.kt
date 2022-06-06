/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.executors

import java.io.Closeable

abstract class ExecutableHandle : Closeable {
    abstract fun join(): ExitStatus
    abstract fun terminate(): ExitStatus.Terminated

    final override fun close() {
        join()
        // TODO: Check ExitStatus
    }
}