/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental.util

import org.jetbrains.kotlin.incremental.LookupSymbol
import org.jetbrains.org.objectweb.asm.ClassReader
//import org.objectweb.asm.ClassReader
//import org.objectweb.asm.Opcodes
//import org.jetbrains.org.objectweb.asm.tree.ClassNode
import java.io.*
import java.util.jar.JarFile
import java.util.zip.ZipInputStream

abstract class JarToJarSnapshotTransform {

    companion object {
        fun filter(jarFile: File, scope: List<String>): Map<String, LookupSymbol> {
            val lookups = HashMap<String, LookupSymbol>()
            load(jarFile).mapValues { classReader ->
//                val node = ClassNode()
//                classReader.value.accept(node, 0)
                val fqName = classReader.key.substringBeforeLast(".class").replace("/", ".")
                if (scope.contains(fqName)) {
                    lookups.put(classReader.key,
                            //add all public methods? or add existed lookups
                        LookupSymbol(
                            fqName,
                            fqName
                        )
                    )
                }
//                lookups.addAll(node.methods
//                                   .filter { (it.access and Opcodes.ACC_PUBLIC) == 1 }
//                                   .map { method ->
//                                       LookupSymbol(
//                                           classReader.key,
//                                           method.name,
//                                           node.name
//                                       )
//                                   }
//                )
//                lookups.putAll(node.fields
//                                   .filter { (it.access and Opcodes.ACC_PUBLIC) == 1 }
//                                   .fil { method ->
//                                       SimpleLookupInfo(
//                                           classReader.key,
//                                           method.name,
//                                           node.name
//                                       )
//                                   }
//                )
            }
            return lookups
        }

//        fun filter(jarFile: File, scope: List<String>): List<String, LookupSymbol> {
//
//        }

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


}




