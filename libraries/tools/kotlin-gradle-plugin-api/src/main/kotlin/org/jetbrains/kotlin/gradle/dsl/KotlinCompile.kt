/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested

interface KotlinCompile<out T : KotlinCommonOptions> : Task {
    @get:Nested
    val kotlinOptions: T

    @get:Internal
    val kotlinOptionsDsl: KotlinOptionsDsl<T>

    fun kotlinOptions(fn: KotlinOptionsDsl<T>.() -> Unit) {
        kotlinOptionsDsl.fn()
    }

    fun kotlinOptions(fn: Action<in KotlinOptionsDsl<T>>) {
        fn.execute(kotlinOptionsDsl)
    }

    fun kotlinOptions(fn: Closure<*>) {
        project.configure(kotlinOptionsDsl, fn)
    }
}
