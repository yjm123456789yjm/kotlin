/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental.util

import org.jetbrains.kotlin.incremental.LookupSymbol
import org.jetbrains.kotlin.incremental.storage.LookupSymbolKey
import org.jetbrains.org.objectweb.asm.ClassReader
import java.io.*
import java.util.jar.JarFile

abstract class JarToJarSnapshotTransform {

    companion object {
        fun filter(jarFile: File, scopesMap: Map<String, List<LookupSymbolKey>>): ArrayList<LookupSymbol> {
            val lookups = ArrayList<LookupSymbol>()
            load(jarFile).mapValues { classReader ->
                val fqName = classReader.key.substringBeforeLast(".class").replace("/", ".")
                val className = fqName.substringAfterLast(".")
                val packageName = fqName.substringBeforeLast(".")

                scopesMap[fqName]?.also {
                    lookups.addAll(it.map { lookup -> LookupSymbol(lookup.name, lookup.scope) })
                }
                scopesMap[packageName]?.also {
                    it.filter { lookup -> lookup.name == className }.forEach { lookup ->
                        lookups.add(LookupSymbol(lookup.name, lookup.scope))
                    }
                }
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

    }
}




