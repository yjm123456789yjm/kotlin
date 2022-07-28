/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.interfaces

import org.jetbrains.kotlin.ir.backend.js.utils.JsAnnotations
import org.jetbrains.kotlin.ir.backend.js.utils.isJsExport
import org.jetbrains.kotlin.ir.backend.js.utils.isJsSubtypeCheckable
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrFileSymbolImpl
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.transformDeclarationsFlat
import org.jetbrains.kotlin.name.FqName

const val FAKE_FILE_NAME = "project-type-checkable-interfaces"

class MoveInstanceCheckableInterfacesAtTheTop(private val module: IrModuleFragment) {
    fun transform() {
        val firstFile = module.files.firstOrNull() ?: return
        val interfacesFile = createFile(firstFile)

        module.files.forEach { file ->
            file.transformDeclarationsFlat {
                if (it is IrClass && it.isInterface && it.isJsSubtypeCheckable()) {
                    interfacesFile.declarations.add(it.withJsExportAndParent(file, interfacesFile))
                    emptyList()
                } else {
                    null
                }
            }
        }

        module.files.add(0, interfacesFile)
    }

    private fun IrClass.withJsExportAndParent(file: IrFile, newParent: IrFile): IrClass {
        return apply {
            parent = newParent
            file.getAnnotation(JsAnnotations.jsExportFqn)?.let { annotations += it }
        }
    }

    private fun createFile(moduleFile: IrFile): IrFile {
        return IrFileImpl(
            fqName = FqName(FAKE_FILE_NAME),
            fileEntry = moduleFile.fileEntry,
            symbol = IrFileSymbolImpl(),
            module = module
        )
    }
}