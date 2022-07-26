/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch") // Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin.mpp

import org.jetbrains.kotlin.gradle.dsl.CompilerCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions

class KotlinWithJavaCompilationFactory<KO : KotlinCommonOptions, CO : CompilerCommonOptions>(
    private val target: KotlinWithJavaTarget<KO, CO>,
    private val compilerOptionsFactory: () -> CO,
    private val kotlinOptionsFactory: CompilationDetails<*>.() -> KO
) : KotlinCompilationFactory<KotlinWithJavaCompilation<KO, CO>> {

    override val itemClass: Class<KotlinWithJavaCompilation<KO, CO>>
        @Suppress("UNCHECKED_CAST")
        get() = KotlinWithJavaCompilation::class.java as Class<KotlinWithJavaCompilation<KO, CO>>

    @Suppress("UNCHECKED_CAST")
    override fun create(name: String): KotlinWithJavaCompilation<KO, CO> =
        target.project.objects.newInstance(
            KotlinWithJavaCompilation::class.java,
            target,
            name,
            compilerOptionsFactory,
            kotlinOptionsFactory
        ) as KotlinWithJavaCompilation<KO, CO>
}