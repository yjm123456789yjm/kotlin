/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.incremental

import java.io.ObjectInputStream
import java.io.ObjectOutputStream

interface JarSnapshot {
    val lookups : Map<String, LookupSymbol>
}

class JarSnapshotImpl(override val lookups : Map<String, LookupSymbol>) : JarSnapshot {
    companion object {
        fun ObjectOutputStream.writeJarSnapshot(jarSnapshot: JarSnapshot) {
            writeInt(jarSnapshot.lookups.size)
            for (lookup in jarSnapshot.lookups) {
                writeUTF(lookup.key)
                writeUTF(lookup.value.name)
                writeUTF(lookup.value.scope)
            }
        }
        fun ObjectInputStream.readJarSnapshot(): JarSnapshotImpl {
            val size = readInt()
            val lookups = hashMapOf<String, LookupSymbol>()
            repeat(size) {
                lookups[readUTF()] = LookupSymbol(readUTF(), readUTF())
            }
            return JarSnapshotImpl(lookups)
        }
    }
}