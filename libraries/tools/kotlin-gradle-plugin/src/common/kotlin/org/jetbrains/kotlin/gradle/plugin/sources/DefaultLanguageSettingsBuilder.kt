/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.sources

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.gradle.dsl.CompilerCommonOptions
import org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinNativeCompile
import org.jetbrains.kotlin.gradle.tasks.toSingleCompilerPluginOptions

internal class DefaultLanguageSettingsBuilder(
    override val compilerOptions: CompilerCommonOptions
) : LanguageSettingsBuilder {
    override var languageVersion: String?
        get() = compilerOptions.languageVersion.orNull?.versionString
        set(value) {
            compilerOptions.languageVersion.set(
                value?.let { versionString ->
                    LanguageVersion.fromVersionString(versionString) ?: throw InvalidUserDataException(
                        "Incorrect language version. Expected one of: ${LanguageVersion.values().joinToString { "'${it.versionString}'" }}"
                    )
                }
            )
        }

    override var apiVersion: String?
        get() = compilerOptions.apiVersion.orNull?.versionString
        set(value) {
            compilerOptions.apiVersion.set(
                value?.let { versionString ->
                    parseApiVersionSettings(versionString) ?: throw InvalidUserDataException(
                        "Incorrect API version. Expected one of: ${apiVersionValues.joinToString { "'${it.versionString}'" }}"
                    )
                }
            )
        }

    override var progressiveMode: Boolean
        get() = compilerOptions.progressiveMode.get()
        set(value) = compilerOptions.progressiveMode.set(value)

    override val enabledLanguageFeatures: Set<String>
        get() = compilerOptions.languageFeatures.get().filterValues { it }.keys.map { it.name }.toSet()

    override fun enableLanguageFeature(name: String) {
        val languageFeature = parseLanguageFeature(name) ?: throw InvalidUserDataException(
            "Unknown language feature '${name}'"
        )
        compilerOptions.languageFeatures.put(languageFeature, true)
    }

    override val optInAnnotationsInUse: Set<String> = compilerOptions.optIn.get().toSet()

    override fun optIn(annotationName: String) {
        compilerOptions.optIn.add(annotationName)
    }

    /* A Kotlin task that is responsible for code analysis of the owner of this language settings builder. */
    @Transient // not needed during Gradle Instant Execution
    var compilerPluginOptionsTask: Lazy<AbstractKotlinCompileTool<*>?> = lazyOf(null)

    val compilerPluginArguments: List<String>?
        get() {
            val pluginOptionsTask = compilerPluginOptionsTask.value ?: return null
            return when (pluginOptionsTask) {
                is AbstractKotlinCompile<*> -> pluginOptionsTask.pluginOptions.toSingleCompilerPluginOptions()
                is AbstractKotlinNativeCompile<*, *, *> -> pluginOptionsTask.compilerPluginOptions
                else -> error("Unexpected task: $pluginOptionsTask")
            }.arguments
        }

    val compilerPluginClasspath: FileCollection?
        get() {
            val pluginClasspathTask = compilerPluginOptionsTask.value ?: return null
            return when (pluginClasspathTask) {
                is AbstractKotlinCompile<*> -> pluginClasspathTask.pluginClasspath
                is AbstractKotlinNativeCompile<*, *, *> -> pluginClasspathTask.compilerPluginClasspath ?: pluginClasspathTask.project.files()
                else -> error("Unexpected task: $pluginClasspathTask")
            }
        }

    val freeCompilerArgs: ListProperty<String>
        get() = compilerOptions.freeCompilerArgs
}

internal fun applyLanguageSettingsToCompilerOptions(
    languageSettingsBuilder: LanguageSettingsBuilder,
    compilerOptions: CompilerCommonOptions
) {
    compilerOptions.languageVersion.convention(languageSettingsBuilder.compilerOptions.languageVersion)
    compilerOptions.apiVersion.convention(languageSettingsBuilder.compilerOptions.apiVersion)
    compilerOptions.progressiveMode.convention(languageSettingsBuilder.compilerOptions.progressiveMode)
    compilerOptions.languageFeatures.putAll(languageSettingsBuilder.compilerOptions.languageFeatures)
    compilerOptions.optIn.addAll(languageSettingsBuilder.compilerOptions.optIn)
    compilerOptions.freeCompilerArgs.addAll(languageSettingsBuilder.compilerOptions.freeCompilerArgs)
}

private val apiVersionValues = ApiVersion.run {
    listOf(
        KOTLIN_1_0,
        KOTLIN_1_1,
        KOTLIN_1_2,
        KOTLIN_1_3,
        KOTLIN_1_4,
        KOTLIN_1_5,
        KOTLIN_1_6,
        KOTLIN_1_7,
        KOTLIN_1_8,
        KOTLIN_1_9
    )
}

internal fun parseLanguageVersionSetting(versionString: String) = LanguageVersion.fromVersionString(versionString)
internal fun parseApiVersionSettings(versionString: String) = apiVersionValues.find { it.versionString == versionString }
internal fun parseLanguageFeature(featureName: String) = LanguageFeature.fromString(featureName)
