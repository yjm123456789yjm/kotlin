/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.utils

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.lower.inline.InlineFunctionResolver
import org.jetbrains.kotlin.backend.common.lower.inline.isBuiltInSuspendCoroutineUninterceptedOrReturn
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.util.resolveFakeOverride

class WasmInlineFunctionResolver(val context: CommonBackendContext) : InlineFunctionResolver {
    override fun getFunctionDeclaration(symbol: IrFunctionSymbol): IrFunction {
        return when {
            symbol.owner.isBuiltInSuspendCoroutineUninterceptedOrReturn() ->
                context.ir.symbols.suspendCoroutineUninterceptedOrReturn.owner

            symbol == context.ir.symbols.coroutineContextGetter ->
                context.ir.symbols.coroutineGetContext.owner

            else -> (symbol.owner as? IrSimpleFunction)?.resolveFakeOverride() ?: symbol.owner
        }
    }
}
