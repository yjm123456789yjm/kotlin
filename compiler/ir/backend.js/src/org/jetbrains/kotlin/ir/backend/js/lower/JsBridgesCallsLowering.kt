/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.JsLoweredDeclarationOrigin
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import java.lang.Integer.min

class JsBridgesCallsLowering(context: JsIrBackendContext) : BodyLoweringPass {
    private val bridgesCallsTransformer = JsBridgesCallsTransformer(context)

    override fun lower(irFile: IrFile) {
        runOnFilePostfix(irFile, true)
    }

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.transformChildrenVoid(bridgesCallsTransformer)
    }

    private class JsBridgesCallsTransformer(val context: JsIrBackendContext) : IrElementTransformerVoidWithContext() {
        override fun visitCall(expression: IrCall): IrExpression {
            return super.visitCall(swapCallSymbolWithBridgeIfNeeded(expression))
        }

        private fun swapCallSymbolWithBridgeIfNeeded(expression: IrCall): IrCall {
            val function = expression.symbol.owner.realOverrideTarget

            if (function.isBridge() || !shouldCallBridgeIfExists(expression)) return expression

            val generatedBridgeForTheFunction = findBridgeForTheFunction(function) ?: return expression

            return context.createIrBuilder(generatedBridgeForTheFunction.symbol)
                .irCall(generatedBridgeForTheFunction.symbol)
                .also { copyArgs(expression, it) }
        }

        private fun copyArgs(from: IrFunctionAccessExpression, into: IrFunctionAccessExpression) {
            into.dispatchReceiver = from.dispatchReceiver
            into.extensionReceiver = from.extensionReceiver

            for (i in 0 until min(into.valueArgumentsCount, from.valueArgumentsCount)) {
                into.putValueArgument(i, from.getValueArgument(i))
            }

            for (i in 0 until min(into.typeArgumentsCount, from.typeArgumentsCount)) {
                into.putTypeArgument(i, from.getTypeArgument(i))
            }
        }

        private fun shouldCallBridgeIfExists(expression: IrCall): Boolean {
            val parent = expression.symbol.owner.parent as? IrClass ?: return false
            val receiver = expression.dispatchReceiver?.type?.classifierOrNull ?: return false
            return receiver.owner != parent
        }

        private fun findBridgeForTheFunction(function: IrSimpleFunction): IrSimpleFunction? {
            val functionParent = function.parent as? IrClass ?: return null

            val bridge = functionParent.declarations.find {
                it is IrSimpleFunction && it.isBridge() && it.overriddenSymbols.containsAll(function.overriddenSymbols)
            }

            return bridge as? IrSimpleFunction
        }

        private fun IrFunction.isBridge(): Boolean {
            return origin == JsLoweredDeclarationOrigin.BRIDGE_WITHOUT_STABLE_NAME ||
                    origin == JsLoweredDeclarationOrigin.BRIDGE_WITH_STABLE_NAME
        }
    }
}