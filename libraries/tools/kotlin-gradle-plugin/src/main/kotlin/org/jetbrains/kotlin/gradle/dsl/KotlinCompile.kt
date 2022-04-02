/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.gradle.dsl

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

interface AbstractKotlinCompile<T : KotlinCommonOptions> : KotlinCompile<T>

interface KotlinJsCompile : AbstractKotlinCompile<KotlinJsOptions>

interface KotlinJvmCompile : AbstractKotlinCompile<KotlinJvmOptions>

interface KotlinCommonCompile : AbstractKotlinCompile<KotlinMultiplatformCommonOptions>

interface KotlinNativeCompile : AbstractKotlinCompile<KotlinCommonOptions>

interface AbstractKotlinTool<T : KotlinCommonToolOptions> : KotlinCompile<T>

interface KotlinJsDce : AbstractKotlinTool<KotlinJsDceOptions> {
    @get:Deprecated("Use 'kotlinOptions' input instead", replaceWith = ReplaceWith("kotlinOptions"))
    @get:Internal
    val dceOptions: KotlinJsDceOptions
        get() = kotlinOptions

    @get:Input
    val keep: MutableList<String>

    fun keep(vararg fqn: String)
}