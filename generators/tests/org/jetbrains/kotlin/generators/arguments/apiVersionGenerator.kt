/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.arguments

import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.Printer
import java.io.File
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMemberProperties

internal fun generateApiVersion(
    apiDir: File,
    filePrinter: (targetFile: File, Printer.() -> Unit) -> Unit
) {
    val apiVersionFqName = FqName("org.jetbrains.kotlin.gradle.dsl.ApiVersion")
    filePrinter(file(apiDir, apiVersionFqName)) {
        generateDeclaration("enum class", apiVersionFqName, afterType = "(val version: String)") {
            val apiVersionProps = ApiVersion::class.companionObject!!
                .declaredMemberProperties
                .filter { it.name.startsWith("KOTLIN") }

            val lastIndex = apiVersionProps.size - 1
            apiVersionProps.forEachIndexed { index, prop ->
                @Suppress("UNCHECKED_CAST")
                val versionString = (prop as KProperty1<Any, ApiVersion>).get(ApiVersion.Companion).versionString
                val lastChar = if (index == lastIndex) ";" else ","
                println("${prop.name.uppercase()}(\"$versionString\")$lastChar")
            }

            println()
            println("companion object {")
            withIndent {
                println("fun fromVersion(version: String): ApiVersion =")
                println("    ApiVersion.values().first { it.version == version }")
            }
            println("}")
        }
    }
}
