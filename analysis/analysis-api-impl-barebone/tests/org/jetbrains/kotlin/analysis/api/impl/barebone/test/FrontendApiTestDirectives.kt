/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.barebone.test

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object FrontendApiTestDirectives : SimpleDirectivesContainer() {
    val IGNORE_FIR by directive("Pass test on FIR frontend silently")
    val IGNORE_FE10 by directive("Pass test on FE10 frontend silently")
}