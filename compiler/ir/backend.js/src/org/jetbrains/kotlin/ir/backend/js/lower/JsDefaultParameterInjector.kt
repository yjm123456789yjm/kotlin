/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.backend.common.lower.DefaultParameterInjector
import org.jetbrains.kotlin.ir.backend.js.JsLoweredDeclarationOrigin
import org.jetbrains.kotlin.ir.backend.js.utils.getVoid
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.isVararg

class JsDefaultParameterInjector(override val context: JsIrBackendContext) :
    DefaultParameterInjector(context, skipExternalMethods = true, forceSetOverrideSymbols = false, keepOriginalArguments = true) {
    override fun nullConst(startOffset: Int, endOffset: Int, irParameter: IrValueParameter): IrExpression? =
        if (irParameter.isVararg && !irParameter.hasDefaultValue()) {
            null
        } else {
            context.getVoid()
        }

    private fun IrValueParameter.hasDefaultValue(): Boolean =
        origin == JsLoweredDeclarationOrigin.JS_SHADOWED_DEFAULT_PARAMETER
}

