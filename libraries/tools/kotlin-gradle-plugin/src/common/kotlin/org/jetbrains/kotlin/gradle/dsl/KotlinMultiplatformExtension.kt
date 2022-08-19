/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl

import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.Companion.kotlinPropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import javax.inject.Inject

abstract class KotlinMultiplatformExtension(project: Project) :
    KotlinProjectExtension(project),
    KotlinTargetContainerWithPresetFunctions,
    KotlinTargetContainerWithJsPresetFunctions,
    KotlinTargetContainerWithWasmPresetFunctions,
    KotlinTargetContainerWithNativeShortcuts {
    override val presets: NamedDomainObjectCollection<KotlinTargetPreset<*>> = project.container(KotlinTargetPreset::class.java)

    final override val targets: NamedDomainObjectCollection<KotlinTarget> = project.container(KotlinTarget::class.java)

    override val defaultJsCompilerType: KotlinJsCompilerType = project.kotlinPropertiesProvider.jsCompiler

    private val presetExtension = project.objects.newInstance(
        DefaultTargetsFromPresetExtension::class.java,
        { this },
        targets
    )

    init {
        val presetExtensionWithDeprecation = project.objects.newInstance(
            TargetsFromPresetExtensionWithDeprecation::class.java,
            project.logger,
            project.path,
            presetExtension
        )
        @Suppress("DEPRECATION")
        DslObject(targets).addConvention("fromPreset", presetExtensionWithDeprecation)
    }

    fun targets(configure: Action<TargetsFromPresetExtension>) {
        configure.execute(presetExtension)
    }

    fun targets(configure: TargetsFromPresetExtension.() -> Unit) {
        configure(presetExtension)
    }

    @Suppress("unused") // DSL
    val testableTargets: NamedDomainObjectCollection<KotlinTargetWithTests<*, *>>
        get() = targets.withType(KotlinTargetWithTests::class.java)

    fun metadata(configure: KotlinOnlyTarget<AbstractKotlinCompilation<*>>.() -> Unit = { }): KotlinOnlyTarget<AbstractKotlinCompilation<*>> =
        @Suppress("UNCHECKED_CAST")
        (targets.getByName(KotlinMultiplatformPlugin.METADATA_TARGET_NAME) as KotlinOnlyTarget<AbstractKotlinCompilation<*>>).also(configure)

    fun metadata(configure: Action<KotlinOnlyTarget<AbstractKotlinCompilation<*>>>) = metadata { configure.execute(this) }

    fun <T : KotlinTarget> targetFromPreset(
        preset: KotlinTargetPreset<T>,
        name: String = preset.name,
        configure: T.() -> Unit = { }
    ): T = configureOrCreate(name, preset, configure)

    fun <T : KotlinTarget> targetFromPreset(
        preset: KotlinTargetPreset<T>,
        name: String,
        configure: Action<T>
    ) = targetFromPreset(preset, name) { configure.execute(this) }

    fun <T : KotlinTarget> targetFromPreset(preset: KotlinTargetPreset<T>) = targetFromPreset(preset, preset.name) { }
    fun <T : KotlinTarget> targetFromPreset(preset: KotlinTargetPreset<T>, name: String) = targetFromPreset(preset, name) { }
    fun <T : KotlinTarget> targetFromPreset(preset: KotlinTargetPreset<T>, configure: Action<T>) =
        targetFromPreset(preset, preset.name, configure)

    internal val rootSoftwareComponent: KotlinSoftwareComponent by lazy {
        KotlinSoftwareComponentWithCoordinatesAndPublication(project, "kotlin", targets)
    }
}

interface TargetsFromPresetExtension : NamedDomainObjectCollection<KotlinTarget> {

    fun <T : KotlinTarget> fromPreset(
        preset: KotlinTargetPreset<T>,
        name: String,
        configureAction: T.() -> Unit = {}
    ): T

    fun <T : KotlinTarget> fromPreset(
        preset: KotlinTargetPreset<T>,
        name: String
    ): T = fromPreset(preset, name, {})

    fun <T : KotlinTarget> fromPreset(
        preset: KotlinTargetPreset<T>,
        name: String,
        configureAction: Action<T>
    ): T
}

internal abstract class DefaultTargetsFromPresetExtension @Inject constructor(
    private val targetsContainer: () -> KotlinTargetsContainerWithPresets,
    val targets: NamedDomainObjectCollection<KotlinTarget>
) : TargetsFromPresetExtension,
    NamedDomainObjectCollection<KotlinTarget> by targets {

    override fun <T : KotlinTarget> fromPreset(
        preset: KotlinTargetPreset<T>,
        name: String,
        configureAction: T.() -> Unit
    ): T = targetsContainer().configureOrCreate(name, preset, configureAction)

    override fun <T : KotlinTarget> fromPreset(
        preset: KotlinTargetPreset<T>,
        name: String,
        configureAction: Action<T>
    ) = fromPreset(preset, name) {
        configureAction.execute(this)
    }
}

internal abstract class TargetsFromPresetExtensionWithDeprecation @Inject constructor(
    private val logger: Logger,
    private val projectPath: String,
    private val parentExtension: DefaultTargetsFromPresetExtension
) : TargetsFromPresetExtension,
    NamedDomainObjectCollection<KotlinTarget> by parentExtension.targets {

    override fun <T : KotlinTarget> fromPreset(
        preset: KotlinTargetPreset<T>,
        name: String,
        configureAction: T.() -> Unit
    ): T {
        printDeprecationMessage(preset, name)
        return parentExtension.fromPreset(preset, name, configureAction)
    }

    override fun <T : KotlinTarget> fromPreset(
        preset: KotlinTargetPreset<T>,
        name: String,
        configureAction: Action<T>
    ): T {
        printDeprecationMessage(preset, name)
        return parentExtension.fromPreset(preset, name, configureAction)
    }

    private fun <T : KotlinTarget> printDeprecationMessage(
        preset: KotlinTargetPreset<T>,
        targetName: String
    ) {
        logger.warn(
            """
            Creating Kotlin target ${preset.name}:${targetName} via convention 'target.fromPreset()' in $projectPath project is deprecated!"
            
            Check https://kotlinlang.org/docs/multiplatform-set-up-targets.html documentation how to create MPP target.
            """.trimIndent()
        )
    }
}
