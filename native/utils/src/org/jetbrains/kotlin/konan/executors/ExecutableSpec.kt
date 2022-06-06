/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.executors

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

/**
 * Parameters for execution.
 *
 * @property executablePath path to the executable
 * @property args command line arguments
 * @property stdin stream to feed into executable as standard input
 * @property stdout stream to feed from executable standard output
 * @property stderr stream to feed from executable standard error
 */
data class ExecutableSpec(
    val executablePath: Path,
    val args: MutableList<String> = mutableListOf(),
    var stdin: InputStream = ByteArrayInputStream(byteArrayOf()),
    var stdout: OutputStream = System.out,
    var stderr: OutputStream = System.err,
)
