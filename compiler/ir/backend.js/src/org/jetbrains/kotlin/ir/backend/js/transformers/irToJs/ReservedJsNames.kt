/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.transformers.irToJs

import org.jetbrains.kotlin.js.backend.ast.JsName
import org.jetbrains.kotlin.js.backend.ast.JsNameIr
import org.jetbrains.kotlin.js.backend.ast.JsNameRef

class ReservedJsNames {
    companion object {
        val internalModuleName = JsNameIr("_")
        val jsExporterName = JsNameIr("\$jsExportAll\$")
        fun makeCrossModuleNameRef(moduleName: JsName) = JsNameRef("\$_\$", moduleName.makeRef())
    }
}
