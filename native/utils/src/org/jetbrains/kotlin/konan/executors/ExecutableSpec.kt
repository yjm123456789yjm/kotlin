/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.executors

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.time.*

@OptIn(ExperimentalTime::class)
data class ExecutableSpec(
    var executablePath: Path,
    val args: MutableList<String> = mutableListOf(),
    var stdin: InputStream = ByteArrayInputStream(byteArrayOf()),
    var stdout: OutputStream = System.out,
    var stderr: OutputStream = System.err,
    var executionTimeout: Duration = Duration.INFINITE,
)
