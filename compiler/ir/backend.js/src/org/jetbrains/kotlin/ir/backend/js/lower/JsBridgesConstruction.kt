/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.JsLoweredDeclarationOrigin
import org.jetbrains.kotlin.ir.backend.js.utils.*
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.interpreter.toIrConst
import org.jetbrains.kotlin.ir.util.primaryConstructor

class JsBridgesConstruction(context: JsIrBackendContext) : BridgesConstruction<JsIrBackendContext>(context) {
    override fun getFunctionSignature(function: IrSimpleFunction) =
        // TODO: remove in future `toString` call
        jsFunctionSignatureWithoutStable(function).toString()

    override fun getBridgeOrigin(bridge: IrSimpleFunction): IrDeclarationOrigin =
        if (bridge.hasStableJsName(context))
            JsLoweredDeclarationOrigin.BRIDGE_WITH_STABLE_NAME
        else
            JsLoweredDeclarationOrigin.BRIDGE_WITHOUT_STABLE_NAME

    override fun manipulateWithOriginalFunctionAfterBridgeCreation(function: IrSimpleFunction) {
        if (!function.hasStableJsName(context)) return
        function.origin = JsLoweredDeclarationOrigin.JS_SHADOWED_EXPORT
    }

}
