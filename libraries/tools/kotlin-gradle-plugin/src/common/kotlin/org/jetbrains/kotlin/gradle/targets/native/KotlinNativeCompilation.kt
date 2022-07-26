/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch") // Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.CompilerCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinNativeCompilationData
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinNativeFragmentMetadataCompilationData
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

internal class NativeCompileOptions(
    override val options: CompilerCommonOptions,
    languageSettingsProvider: () -> LanguageSettingsBuilder
) : KotlinCommonOptions {

    init {
        options.useK2.finalizeValue()
    }

    // TODO: change language settings!
    private val languageSettings: LanguageSettingsBuilder by lazy(languageSettingsProvider)

    override var apiVersion: String?
        get() = languageSettings.apiVersion
        set(value) {
            languageSettings.apiVersion = value
        }

    override var languageVersion: String?
        get() = languageSettings.languageVersion
        set(value) {
            languageSettings.languageVersion = value
        }
    
    override var useK2: Boolean
        get() = options.useK2.get()
        set(value) = options.useK2.set(value)

    override var allWarningsAsErrors: Boolean
        get() = options.allWarningsAsErrors.get()
        set(value) = options.allWarningsAsErrors.set(value)

    override var suppressWarnings: Boolean
        get() = options.suppressWarnings.get()
        set(value) = options.suppressWarnings.set(value)

    override var verbose: Boolean
        get() = options.verbose.get()
        set(value) = options.verbose.set(value)

    override var freeCompilerArgs: List<String>
        get() = options.freeCompilerArgs.get()
        set(value) = options.freeCompilerArgs.set(value)
}

abstract class AbstractKotlinNativeCompilation(
    override val konanTarget: KonanTarget,
    compilationDetails: CompilationDetails<KotlinCommonOptions>
) : AbstractKotlinCompilation<KotlinCommonOptions>(
    compilationDetails
),
    KotlinNativeCompilationData<KotlinCommonOptions> {

    override val compileKotlinTask: KotlinNativeCompile
        get() = super.compileKotlinTask as KotlinNativeCompile

    @Suppress("UNCHECKED_CAST")
    override val compileKotlinTaskProvider: TaskProvider<out KotlinNativeCompile>
        get() = super.compileKotlinTaskProvider as TaskProvider<out KotlinNativeCompile>

    internal val useGenericPluginArtifact: Boolean
        get() = project.nativeUseEmbeddableCompilerJar

    // Endorsed library controller.
    override var enableEndorsedLibs: Boolean = false
}

internal val Project.nativeUseEmbeddableCompilerJar: Boolean
    get() = PropertiesProvider(this).nativeUseEmbeddableCompilerJar

internal fun addSourcesToKotlinNativeCompileTask(
    project: Project,
    taskName: String,
    sourceFiles: () -> Iterable<File>,
    addAsCommonSources: Lazy<Boolean>
) {
    project.tasks.withType(KotlinNativeCompile::class.java).matching { it.name == taskName }.configureEach { task ->
        task.setSource(sourceFiles)
        task.commonSources.from(project.files(Callable { if (addAsCommonSources.value) sourceFiles() else emptyList() }))
    }

}

abstract class KotlinNativeCompilation @Inject constructor(
    konanTarget: KonanTarget,
    details: CompilationDetails<KotlinCommonOptions>
) : AbstractKotlinNativeCompilation(konanTarget, details),
    KotlinCompilationWithResources<KotlinCommonOptions> {

    override val target: KotlinNativeTarget
        get() = super.target as KotlinNativeTarget

    // Interop DSL.
    val cinterops = project.container(DefaultCInteropSettings::class.java) { cinteropName ->
        project.objects.newInstance(DefaultCInteropSettings::class.java, project, cinteropName, this)
    }

    fun cinterops(action: Action<NamedDomainObjectContainer<DefaultCInteropSettings>>) = action.execute(cinterops)

    // Naming
    override val processResourcesTaskName: String
        get() = disambiguateName("processResources")

    val binariesTaskName: String
        get() = lowerCamelCaseName(target.disambiguationClassifier, compilationPurpose, "binaries")
}

abstract class KotlinSharedNativeCompilation @Inject constructor(
    val konanTargets: List<KonanTarget>,
    compilationDetails: CompilationDetails<KotlinCommonOptions>
) : KotlinNativeFragmentMetadataCompilationData,
    AbstractKotlinNativeCompilation(
        // TODO: this will end up as '-target' argument passed to K2Native, which is wrong.
        // Rewrite this when we'll compile native-shared source-sets against commonized platform libs
        // We find any konan target that is enabled on the current host in order to pass the checks that avoid compiling the code otherwise.
        konanTargets.find { it.enabledOnCurrentHost } ?: konanTargets.first(),
        compilationDetails
    ),
    KotlinMetadataCompilation<KotlinCommonOptions> {

    override fun getName() =
        if (compilationDetails is MetadataMappedCompilationDetails) defaultSourceSetName else super.compilationPurpose

    override val target: KotlinMetadataTarget get() = super.target as KotlinMetadataTarget

    override val isActive: Boolean
        get() = true // old plugin only creates necessary compilations
}
