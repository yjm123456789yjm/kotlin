/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.jvm.compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import java.lang.IllegalStateException

fun Project.setupHighestLanguageLevel() {
    val languageLevelExtension = LanguageLevelProjectExtension.getInstance(this)

    val applicableLanguageLevels = listOf("JDK_15_PREVIEW", "JDK_15")
    for (languageLevelName in applicableLanguageLevels) {
        val languageLevel = LanguageLevel.values().find { it.name == languageLevelName } ?: continue
        languageLevelExtension.languageLevel = languageLevel
        return
    }

    throw IllegalStateException("Unable to set the latest Java language level, tried $applicableLanguageLevels")
}
