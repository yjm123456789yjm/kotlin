/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.internal

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.commonizer.SharedCommonizerTarget
import org.jetbrains.kotlin.commonizer.identityString
import org.jetbrains.kotlin.gradle.utils.filesProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject

open class CInteropMetadataDiscoveryTask @Inject constructor(
    @get:Input val target: SharedCommonizerTarget,
    @get:Classpath val metadataConfiguration: Configuration
) : DefaultTask() {

    private val outputDirectory = project.buildDir.resolve("kotlinCInteropMetadata").resolve(target.identityString)

    @get:OutputFiles
    val outputFiles: FileCollection by lazy {
        project.filesProvider {
            outputDirectory.listFiles().orEmpty().toList()
        }.builtBy(this)
    }

    @TaskAction
    protected fun discoverAndExtractCInteropMetadata() {
        metadataConfiguration.files.forEach { file -> discoverAndExtractCInteropMetadata(file) }
    }

    private fun discoverAndExtractCInteropMetadata(metadataArchive: File) {
        if (metadataArchive.isFile.not()) return
        if (metadataArchive.extension !in setOf("klib", "zip", "jar")) return
        ZipFile(metadataArchive).use zip@{ zipFile ->
            val librariesDirectory = zipFile.getEntry("cinterop/$target") ?: return@zip
            if (librariesDirectory.isDirectory.not()) return@zip

            val libraries = zipFile.entries().asSequence().filter { entry ->
                entry.name.startsWith(librariesDirectory.name) &&
                        entry.name.removePrefix(librariesDirectory.name).count { it == '/' } == 1
            }

            outputDirectory.mkdirs()

            libraries.forEach forEachLibrary@{ library ->
                val libraryContent = zipFile.entries().asSequence().filter { entry ->
                    entry.name.startsWith(library.name) && entry.name != library.name
                }.toList()

                if (libraryContent.isEmpty()) {
                    return@forEachLibrary
                }

                val libraryName = library.name.removeSurrounding(librariesDirectory.name, "/")
                val libraryDestinationFile = outputDirectory.resolve("$libraryName.klib")
                ZipOutputStream(libraryDestinationFile.outputStream()).use { libraryDestinationStream ->
                    libraryContent.forEach { libraryContentEntry ->
                        val destinationEntry = ZipEntry(libraryContentEntry.name.removePrefix(library.name))
                        zipFile.getInputStream(libraryContentEntry).use { libraryContentInputStream ->
                            libraryDestinationStream.putNextEntry(destinationEntry)
                            libraryContentInputStream.copyTo(libraryDestinationStream)
                            libraryDestinationStream.closeEntry()
                        }
                    }
                }
            }
        }
    }
}
