/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsCompilerAttribute
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget

internal fun Configuration.setupConsumableKotlinLibraryElements(
    target: KotlinTarget,
    usage: Usage
) {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes.attribute(USAGE_ATTRIBUTE, usage)
    attributes.attribute(CATEGORY_ATTRIBUTE, target.project.categoryByName(Category.LIBRARY))
    setupKotlinTarget(target)
}

internal fun Configuration.setupResolvableKotlinLibraryDependencies(
    target: KotlinTarget,
    usage: Usage
) {
    setupKotlinTarget(target)
    setupResolvableKotlinLibraryDependencies(target.project, usage)
}

internal fun Configuration.setupResolvableKotlinLibraryDependencies(
    project: Project,
    usage: Usage? = null
) {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes.attribute(CATEGORY_ATTRIBUTE, project.categoryByName(Category.LIBRARY))
    if (usage != null) attributes.attribute(USAGE_ATTRIBUTE, usage)
}

fun Configuration.setupKotlinTarget(target: KotlinTarget) {
    attributes.attribute(KotlinPlatformType.attribute, target.platformType)

    if (target is KotlinJsTarget) {
        attributes.attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.legacy)
    }

    if (target is KotlinJsIrTarget) {
        attributes.attribute(KotlinJsCompilerAttribute.jsCompilerAttribute, KotlinJsCompilerAttribute.ir)
    }

    if (target is KotlinNativeTarget) {
        attributes.attribute(KotlinNativeTarget.konanTargetAttribute, target.konanTarget.name)
    }
}

@Deprecated(
    "Scheduled for removal in Kotlin 1.7",
    replaceWith = ReplaceWith("setupKotlinTarget(target)"),
    level = DeprecationLevel.ERROR
)
fun Configuration.usesPlatformOf(target: KotlinTarget): Configuration {
    return apply { setupKotlinTarget(target) }
}