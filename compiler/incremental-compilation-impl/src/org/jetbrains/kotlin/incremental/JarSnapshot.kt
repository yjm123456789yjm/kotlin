/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental

import java.io.ObjectInputStream
import java.io.ObjectOutputStream

interface JarSnapshot {
    val lookups: Map<String, List<LookupSymbol>>
}

class JarSnapshotImpl(internal val lookupsMap: HashMap<String, ArrayList<LookupSymbol>>) : JarSnapshot {
    companion object {
        fun ObjectOutputStream.writeJarSnapshot(jarSnapshot: JarSnapshot) {
            writeInt(jarSnapshot.lookups.size)
            for ((classFile, lookupList) in jarSnapshot.lookups) {
                writeUTF(classFile)
                writeInt(lookupList.size)
                for (lookup in lookupList) {
                    writeUTF(lookup.name)
                    writeUTF(lookup.scope)
                }
            }
        }

        fun ObjectInputStream.readJarSnapshot(): JarSnapshotImpl {
            val size = readInt()
            val lookups = HashMap<String, ArrayList<LookupSymbol>>()
            repeat(size) {
                val classFile = readUTF()
                val lookupSize = readInt()
                val lookupsList = ArrayList<LookupSymbol>(lookupSize)
                repeat(lookupSize) {
                    lookupsList.add(LookupSymbol(readUTF(), readUTF()))

                }
                lookups[classFile] = lookupsList
            }
            return JarSnapshotImpl(lookups)
        }
    }

    override val lookups: HashMap<String, ArrayList<LookupSymbol>>
        get() = lookupsMap
}