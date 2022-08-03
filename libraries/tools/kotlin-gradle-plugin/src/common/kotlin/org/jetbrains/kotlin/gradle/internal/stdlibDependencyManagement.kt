/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal

import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension
import org.jetbrains.kotlin.gradle.plugin.AbstractAndroidProjectHandler.Companion.kotlinSourceSetNameForAndroidSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.isTest
import org.jetbrains.kotlin.gradle.plugin.mpp.kotlinSourceSetsIncludingDefault
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.GradleKpmFragment
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.GradleKpmModule
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.hasKpmModel
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.kpmModules
import org.jetbrains.kotlin.gradle.plugin.sources.KotlinDependencyScope
import org.jetbrains.kotlin.gradle.plugin.sources.dependsOnClosure
import org.jetbrains.kotlin.gradle.plugin.sources.sourceSetDependencyConfigurationByScope
import org.jetbrains.kotlin.gradle.targets.js.npm.SemVer

internal fun Project.configureStdlibDefaultDependency(
    topLevelExtension: KotlinTopLevelExtension,
    coreLibrariesVersion: Provider<String>
) {

    when {
        project.hasKpmModel -> addStdlibToKpmProject(project, coreLibrariesVersion)
        topLevelExtension is KotlinJsProjectExtension -> topLevelExtension.registerTargetObserver { target ->
            target?.addStdlibDependency(configurations, dependencies, coreLibrariesVersion)
        }
        topLevelExtension is KotlinSingleTargetExtension<*> -> topLevelExtension
            .target
            .addStdlibDependency(configurations, dependencies, coreLibrariesVersion)

        topLevelExtension is KotlinMultiplatformExtension -> topLevelExtension
            .targets
            .configureEach { target ->
                target.addStdlibDependency(configurations, dependencies, coreLibrariesVersion)
            }
    }
}

private fun addStdlibToKpmProject(
    project: Project,
    coreLibrariesVersion: Provider<String>
) {
    project.kpmModules.named(GradleKpmModule.MAIN_MODULE_NAME) { main ->
        main.fragments.named(GradleKpmFragment.COMMON_FRAGMENT_NAME) { common ->
            common.dependencies {
                api(project.dependencies.kotlinDependency("kotlin-stdlib-common", coreLibrariesVersion.get()))
            }
        }
        main.variants.configureEach { variant ->
            val dependencyHandler = project.dependencies
            val stdlibModule = when (variant.platformType) {
                KotlinPlatformType.common -> error("variants are not expected to be common")
                KotlinPlatformType.jvm -> chooseStdlibJvmDependency(coreLibrariesVersion)
                KotlinPlatformType.js -> "kotlin-stdlib-js"
                KotlinPlatformType.wasm -> "kotlin-stdlib-wasm"
                KotlinPlatformType.androidJvm -> null // TODO: expect support on the AGP side?
                KotlinPlatformType.native -> null
            }
            if (stdlibModule != null) {
                variant.dependencies {
                    api(dependencyHandler.kotlinDependency(stdlibModule, coreLibrariesVersion.get()))
                }
            }
        }
    }
}

private fun KotlinTarget.addStdlibDependency(
    configurations: ConfigurationContainer,
    dependencies: DependencyHandler,
    coreLibrariesVersion: Provider<String>
) {
    compilations.configureEach { compilation ->
        compilation.allKotlinSourceSets.forEach { kotlinSourceSet ->
            val scope = if (compilation.isTest() ||
                (this is KotlinAndroidTarget &&
                        kotlinSourceSet.isRelatedToAndroidTestSourceSet(compilation as KotlinJvmAndroidCompilation)
                        )
            ) {
                KotlinDependencyScope.IMPLEMENTATION_SCOPE
            } else {
                KotlinDependencyScope.API_SCOPE
            }
            val scopeConfiguration = configurations
                .sourceSetDependencyConfigurationByScope(kotlinSourceSet, scope)

            scopeConfiguration.withDependencies { dependencySet ->
                // Check if stdlib is directly added to SourceSet
                if (isStdlibAddedByUser(configurations, stdlibModules, kotlinSourceSet)) return@withDependencies

                val stdlibModule = compilation
                    .platformType
                    .stdlibPlatformType(coreLibrariesVersion, this, kotlinSourceSet)
                    ?: return@withDependencies

                // Check if stdlib module is added to SourceSets hierarchy
                if (
                    isStdlibAddedByUser(
                        configurations,
                        setOf(stdlibModule),
                        *kotlinSourceSet.dependsOnClosure.toTypedArray()
                    )
                ) return@withDependencies

                dependencySet.addLater(
                    coreLibrariesVersion.map {
                        dependencies.kotlinDependency(stdlibModule, it)
                    }
                )
            }
        }
    }
}

private fun isStdlibAddedByUser(
    configurations: ConfigurationContainer,
    stdlibModules: Set<String>,
    vararg sourceSets: KotlinSourceSet
): Boolean {
    return sourceSets
        .asSequence()
        .flatMap { sourceSet ->
            KotlinDependencyScope.values().map { scope ->
                configurations.sourceSetDependencyConfigurationByScope(sourceSet, scope)
            }.asSequence()
        }
        .flatMap { it.allNonProjectDependencies().asSequence() }
        .any { dependency ->
            dependency.group == KOTLIN_MODULE_GROUP && dependency.name in stdlibModules
        }
}

private fun KotlinPlatformType.stdlibPlatformType(
    coreLibrariesVersion: Provider<String>,
    kotlinTarget: KotlinTarget,
    kotlinSourceSet: KotlinSourceSet
): String? = when (this) {
    KotlinPlatformType.jvm -> chooseStdlibJvmDependency(coreLibrariesVersion)
    KotlinPlatformType.androidJvm -> {
        if (kotlinTarget is KotlinAndroidTarget &&
            kotlinSourceSet.name == kotlinSourceSetNameForAndroidSourceSet(kotlinTarget, "main")
        ) {
            chooseStdlibJvmDependency(coreLibrariesVersion)
        } else {
            null
        }
    }

    KotlinPlatformType.js -> "kotlin-stdlib-js"
    KotlinPlatformType.wasm -> "kotlin-stdlib-wasm"
    KotlinPlatformType.native -> null
    KotlinPlatformType.common -> // there's no platform compilation that the source set is default for
        "kotlin-stdlib-common"
}

private val kotlin180Version = SemVer(1.toBigInteger(), 8.toBigInteger(), 0.toBigInteger())

private fun chooseStdlibJvmDependency(
    coreLibrariesVersion: Provider<String>
): String {
    // Current 'SemVer.satisfies' release always returns `false` for any "-SNAPSHOT" version.
    return if (SemVer.from(coreLibrariesVersion.get()) < kotlin180Version) {
        "kotlin-stdlib-jdk8"
    } else {
        "kotlin-stdlib"
    }
}

private fun KotlinSourceSet.isRelatedToAndroidTestSourceSet(
    compilation: KotlinJvmAndroidCompilation
): Boolean {
    val androidVariant = compilation.androidVariant
    return (androidVariant is UnitTestVariant || androidVariant is TestVariant) &&
            compilation.kotlinSourceSetsIncludingDefault.any { it == this }
}

private val stdlibModules = setOf(
    "kotlin-stdlib-common",
    "kotlin-stdlib",
    "kotlin-stdlib-jdk7",
    "kotlin-stdlib-jdk8",
    "kotlin-stdlib-js"
)
