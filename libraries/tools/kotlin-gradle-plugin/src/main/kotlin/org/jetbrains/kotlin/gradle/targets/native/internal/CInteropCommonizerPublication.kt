/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.*
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.commonizer.SharedCommonizerTarget
import org.jetbrains.kotlin.commonizer.identityString
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtensionOrNull
import org.jetbrains.kotlin.gradle.plugin.categoryByName
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinSharedNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.usageByName
import org.jetbrains.kotlin.gradle.plugin.usesPlatformOf
import org.jetbrains.kotlin.gradle.plugin.whenEvaluated
import java.io.Serializable
import java.util.zip.ZipFile

internal val commonizerTargetAttribute = Attribute.of("commonizer-target", SharedCommonizerTarget::class.java)

internal val artifactTypeAttribute = Attribute.of("artifactType", String::class.java)

internal val cinteropMetadataArtifactTypeAttribute =
    Attribute.of("cinterop-metadata-artifact-type", CInteropMetadataArtifactType::class.java)

enum class CInteropMetadataArtifactType : Named, Serializable {
    Bundle, Directory;

    override fun toString(): String = name
    override fun getName(): String = name
}

internal class CommonizerTargetAttributeCompatibilityRule : AttributeCompatibilityRule<SharedCommonizerTarget> {
    override fun execute(details: CompatibilityCheckDetails<SharedCommonizerTarget>) {
        val consumerValue = details.consumerValue ?: return details.incompatible()
        val producerValue = details.producerValue ?: return details.incompatible()

        /*
        if (producerValue.targets.containsAll(consumerValue.targets)) {
            return details.compatible()
        }
         */

        return details.incompatible()
    }
}

internal class CommonizerTargetAttributeDisambiguationRule : AttributeDisambiguationRule<SharedCommonizerTarget> {
    override fun execute(details: MultipleCandidatesDetails<SharedCommonizerTarget>) {
        val requestedTarget = details.consumerValue ?: return

        if (details.candidateValues.any { candidate -> candidate == requestedTarget }) {
            details.closestMatch(requestedTarget)
            return
        }

        details.closestMatch(details.candidateValues.minByOrNull { candidate -> candidate.targets.size }!!)
    }
}

internal fun Project.setupCommonizerTargetAttribute() {
    dependencies.attributesSchema.attribute(commonizerTargetAttribute) { strategy ->
        strategy.compatibilityRules.add(CommonizerTargetAttributeCompatibilityRule::class.java)
        strategy.disambiguationRules.add(CommonizerTargetAttributeDisambiguationRule::class.java)
    }

    dependencies.attributesSchema.attribute(cinteropMetadataArtifactTypeAttribute)

    dependencies.registerTransform(MetadataDiscoveryTransformer::class.java) { spec ->
        spec.from.attribute(cinteropMetadataArtifactTypeAttribute, CInteropMetadataArtifactType.Bundle)
        spec.to.attribute(cinteropMetadataArtifactTypeAttribute, CInteropMetadataArtifactType.Directory)
    }
}

internal fun Project.setupCInteropCommonizerPublication() {
    whenEvaluated {
        val kotlin = multiplatformExtensionOrNull ?: return@whenEvaluated
        kotlin.forAllSharedNativeCompilations { compilation ->
            setupCInteropCommonizerPublication(compilation)
        }
    }
}

abstract class MetadataDiscoveryTransformer : TransformAction<TransformParameters.None> {

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        ZipFile(input.get().asFile).use { zipFile ->
            val libraryEntries = zipFile.entries().asSequence().filter { it.isDirectory && it.name.count { it == '/' } == 1 }.toList()
            libraryEntries.forEach { libraryEntry ->
                val outputLibraryDirectory = outputs.dir(libraryEntry.name)
                zipFile.entries().asSequence().filter { it.name.startsWith(libraryEntry.name) && it != libraryEntry }
                    .forEach { contentEntry ->
                        zipFile.getInputStream(contentEntry).use { contentEntryInputStream ->
                            val contentEntryOutputFile = outputLibraryDirectory.resolve(contentEntry.name.removePrefix(libraryEntry.name))
                            contentEntryOutputFile.parentFile.mkdirs()
                            contentEntryOutputFile.outputStream().use { contentEntryOutputStream ->
                                contentEntryInputStream.copyTo(contentEntryOutputStream)
                            }
                        }
                    }
            }
        }
    }
}

private fun Project.setupCInteropCommonizerPublication(compilation: KotlinSharedNativeCompilation) {
    val cinteropDependent = CInteropCommonizerDependent.from(compilation) ?: return
    val commonizerTask = commonizeCInteropTask?.get() ?: return
    val outputDirectory = commonizerTask.commonizedOutputDirectory(cinteropDependent) ?: return

    val packageTask = tasks.register("package${compilation.name}CInteropCommonizerElements", Zip::class.java) { zip ->
        zip.from(outputDirectory)
        zip.dependsOn(commonizerTask)
        zip.destinationDirectory.set(project.buildDir.resolve("classes/kotlin/metadata/${compilation.name}"))
        zip.archiveBaseName.set("${compilation.name}CInteropMetadata")
        zip.archiveExtension.set("klib")
    }

    val cinteropCommonizerElements = configurations.create("${compilation.name}CInteropCommonizerElements") { configuration ->
        configuration.isCanBeResolved = false
        configuration.isCanBeConsumed = true
        configuration.usesPlatformOf(compilation.target)
        configuration.attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.categoryByName(Category.LIBRARY))
        configuration.attributes.attribute(commonizerTargetAttribute, cinteropDependent.target)
        configuration.attributes.attribute(Usage.USAGE_ATTRIBUTE, usageByName("commonizer"))
        configuration.attributes.attribute(Usage.USAGE_ATTRIBUTE, usageByName(cinteropDependent.target.identityString))
        configuration.attributes.attribute(artifactTypeAttribute, "klib")
        configuration.attributes.attribute(cinteropMetadataArtifactTypeAttribute, CInteropMetadataArtifactType.Bundle)
    }

    artifacts.add(cinteropCommonizerElements.name, packageTask) {
        it.extension = "klib"
    }
}

internal fun Project.locateOrRegisterCInteropCommonizerDependenciesConfiguration(
    compilation: KotlinSharedNativeCompilation
): Configuration? {
    val commonizerTarget = getCommonizerTarget(compilation) as? SharedCommonizerTarget ?: return null
    return configurations.maybeCreate("${compilation.name}CInteropCommonizerDependencies").also { configuration ->
        configuration.isCanBeResolved = true
        configuration.isCanBeConsumed = false
        configuration.usesPlatformOf(compilation.target)
        configuration.attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.categoryByName(Category.LIBRARY))
        configuration.attributes.attribute(commonizerTargetAttribute, commonizerTarget)
        configuration.attributes.attribute(Usage.USAGE_ATTRIBUTE, usageByName("commonizer"))
        configuration.attributes.attribute(artifactTypeAttribute, "klib")
        configuration.attributes.attribute(cinteropMetadataArtifactTypeAttribute, CInteropMetadataArtifactType.Directory)
    }
}
