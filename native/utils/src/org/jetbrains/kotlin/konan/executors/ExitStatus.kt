/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.executors

import kotlin.time.*

@OptIn(ExperimentalTime::class)
sealed class ExitStatus {
    class Success(override val executionTime: Duration) : ExitStatus()
    class Error(override val executionTime: Duration, val code: Int) : ExitStatus()
    class Timeout(override val executionTime: Duration, val executionTimeout: Duration) : ExitStatus()
    class Terminated(override val executionTime: Duration) : ExitStatus()

    abstract val executionTime: Duration
}
