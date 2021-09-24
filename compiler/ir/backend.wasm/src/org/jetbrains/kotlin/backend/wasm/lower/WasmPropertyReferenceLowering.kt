/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.lower

import org.jetbrains.kotlin.backend.common.lower.PropertyDelegationLowering
import org.jetbrains.kotlin.backend.common.lower.PropertyDelegationLowering.KTypeGeneratorInterface
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrExpression

internal class WasmPropertyReferenceLowering(
    val backendContext: WasmBackendContext
) : PropertyDelegationLowering(backendContext) {
    override fun createKTypeGenerator(irFile: IrFile, expression: IrExpression): KTypeGeneratorInterface {
        return KTypeGeneratorInterface { this.irCall(backendContext.wasmSymbols.kTypeStub) }
    }

    override fun IrField.posProcessKPropertiesField() {
    }
}
