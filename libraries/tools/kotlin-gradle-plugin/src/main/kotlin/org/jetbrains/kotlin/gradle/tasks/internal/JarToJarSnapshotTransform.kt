/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.internal

import org.gradle.api.artifacts.transform.*
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.*
import java.util.jar.JarFile
import java.util.zip.ZipInputStream

const val JAR_SNAPSHOT_ARTIFACT_TYPE = "jar-snapshot"

@CacheableTransform
abstract class JarToJarSnapshotTransform : TransformAction<JarToJarSnapshotTransform.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var jarToModuleAbiSnapshot: HashMap<File, File>
    }


    companion object {
        fun storeLookups(jarFile: File): List<SimpleLookupInfo> {
            val lookups = ArrayList<SimpleLookupInfo>()
            load(jarFile).mapValues { classReader ->
                val node = ClassNode()
                classReader.value.accept(node, 0)
                lookups.add(
                    SimpleLookupInfo(
                        classReader.key,
                        classReader.key,
                        classReader.key
                    )
                )
                lookups.addAll(node.methods
                                   .filter { (it.access and Opcodes.ACC_PUBLIC) == 1 }
                                   .map { method ->
                                       SimpleLookupInfo(
                                           classReader.key,
                                           method.name,
                                           node.name
                                       )
                                   }
                )
                lookups.addAll(node.fields
                                   .filter { (it.access and Opcodes.ACC_PUBLIC) == 1 }
                                   .map { method ->
                                       SimpleLookupInfo(
                                           classReader.key,
                                           method.name,
                                           node.name
                                       )
                                   }
                )
            }
            return lookups
        }


        fun load(jarFile: File): Map<String, ClassReader> {
            val classReaderMap: MutableMap<String, ClassReader> = HashMap()
            JarFile(jarFile).use { jar ->
                val enumeration = jar.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        val reader = ClassReader(jar.getInputStream(entry))
                        classReaderMap[entry.name] = reader
                    }
                }
            }
            return classReaderMap
        }

        fun storeLookups(jarFile: File, lookupCacheDir: File) {
            val classNames: MutableList<String> = ArrayList()
            val zip = ZipInputStream(FileInputStream(jarFile))
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".class")) {
                    // This ZipEntry represents a class. Now, what class does it represent?
                    val className: String = entry.getName().replace('/', '.') // including ".class"
                    classNames.add(className.substring(0, className.length - ".class".length))

                }
                entry = zip.nextEntry
            }

            classNames.forEach { lookupCacheDir.writeText("$it\n") }


        }
    }

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val jarFile = inputArtifact.get().asFile

        val abiSnapshot = parameters.jarToModuleAbiSnapshot[jarFile]
        if (abiSnapshot != null) {
            outputs.file(abiSnapshot)
            return;
        }

        try {
            val lookups = storeLookups(jarFile)
            val lookupCacheDir = outputs.file(jarFile.name.replace('.', '_') + ".jar-snapshot")

            lookupCacheDir.writeText("${jarFile.path} \n")
            lookupCacheDir.writeText("${lookups.size} \n")

            lookups.forEach {
                lookupCacheDir.writeText("${it.filePath}:${it.name}:${it.scope}")
            }
        } catch (_: FileNotFoundException) {
            //module jar will be changed to abiSnapshot
        } catch (_: Exception) {
            outputs.file(jarFile)
        }
    }

    data class SimpleLookupInfo(
        val filePath: String,
        val scope: String,
        val name: String
    ) : Serializable

}




