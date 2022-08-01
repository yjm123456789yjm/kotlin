/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.backend.common.lower.DefaultParameterInjector
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType

class JsDefaultParameterInjector(context: JsIrBackendContext) :
    DefaultParameterInjector(context, skipExternalMethods = true, forceSetOverrideSymbols = false, isMaskRequired = false) {
    private val void = context.intrinsics.void

    override fun nullConst(startOffset: Int, endOffset: Int, type: IrType): IrExpression =
        IrGetFieldImpl(
            startOffset,
            endOffset,
            void.owner.backingField!!.symbol,
            context.irBuiltIns.anyNType
        )
}

