/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch") // Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationWithResources
import org.jetbrains.kotlin.gradle.plugin.internal.JavaSourceSetsAccessor
import org.jetbrains.kotlin.gradle.plugin.variantImplementationFactory
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import javax.inject.Inject

abstract class KotlinJvmCompilation @Inject constructor(
    compilationDetails: CompilationDetailsWithRuntime<KotlinJvmOptions>,
) : AbstractKotlinCompilationToRunnableFiles<KotlinJvmOptions>(compilationDetails), KotlinCompilationWithResources<KotlinJvmOptions> {
    override val target: KotlinJvmTarget get() = compilationDetails.target as KotlinJvmTarget

    override val processResourcesTaskName: String
        get() = disambiguateName("processResources")

    @Suppress("UNCHECKED_CAST")
    override val compileKotlinTaskProvider: TaskProvider<out org.jetbrains.kotlin.gradle.tasks.KotlinCompile>
        get() = super.compileKotlinTaskProvider as TaskProvider<out org.jetbrains.kotlin.gradle.tasks.KotlinCompile>

    override val compileKotlinTask: org.jetbrains.kotlin.gradle.tasks.KotlinCompile
        get() = super.compileKotlinTask as org.jetbrains.kotlin.gradle.tasks.KotlinCompile

    val compileJavaTaskProvider: TaskProvider<out JavaCompile>?
        get() = if (target.withJavaEnabled) {
            val project = target.project
            val javaSourceSets = project.gradle.variantImplementationFactory<JavaSourceSetsAccessor.JavaSourceSetsAccessorVariantFactory>()
                .getInstance(project)
                .sourceSets
            val javaSourceSet = javaSourceSets.getByName(compilationPurpose)
            project.tasks.withType(JavaCompile::class.java).named(javaSourceSet.compileJavaTaskName)
        } else null
}