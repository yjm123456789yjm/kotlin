/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.hierarchy

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithPresetFunctions
import org.jetbrains.kotlin.gradle.plugin.ExperimentalKotlinGradlePluginApi

/**
 * Targets declared within this scope will automatically create shared Kotlin source sets based upon
 * the 'natural' hierarchy:
 *
 * ```
 *                                                   common
 *
 *                                                     |
 *         +----------------------+--------------------+-----------------------+-------------------------+
 *         |                      |                    |                       |                         |
 *                                                                                                       |
 *       apple                  linux              windows              androidNative                   ...
 *
 *         |
 *  +-----------+------------+------------+
 *  |           |            |            |
 *
 * macos       ios         tvos        watchos
 * ```
 *
 * ### Example: Sharing code for ios
 *
 * ```kotlin
 * kotlin {
 *        withNaturalHierarchy {
 *            iosX64()
 *            iosArm64()
 *            iosSimulatorArm64()
 *        }
 * }
 * ```
 *
 * This will automatically create all shared source sets in the tree above (e.g. `iosMain` and `iosTest`)
 */
@ExperimentalKotlinGradlePluginApi
fun KotlinMultiplatformExtension.withNaturalHierarchy(
    configure: KotlinTargetContainerWithHierarchy.() -> Unit
) = withHierarchy(naturalKotlinTargetHierarchy, configure)