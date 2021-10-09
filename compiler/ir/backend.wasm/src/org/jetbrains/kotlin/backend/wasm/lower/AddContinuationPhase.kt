/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.lower

import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.ir.*
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.backend.js.JsCommonBackendContext
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid


class SuspendFunctionCallsLowering(val context: JsCommonBackendContext) : BodyLoweringPass {
    override fun lower(irBody: IrBody, container: IrDeclaration) {

        val continuation by lazy(fun(): IrValueParameter? {
            val function = container as? IrSimpleFunction
                ?: return null

            if (function.overriddenSymbols.any {
                    it.owner.name.asString() == "doResume" && it.owner.parent == context.coroutineSymbols.coroutineImpl.owner
                }) {
                return function.dispatchReceiverParameter
            }

            val continuationParameter = function.valueParameters.lastOrNull()
                ?: return null
            if (continuationParameter.origin == IrDeclarationOrigin.CONTINUATION)
                return continuationParameter


            return null
        })

        val builder by lazy { context.createIrBuilder(container.symbol) }

        fun getContinuation() = builder.irGet(
            continuation
                ?: error("No continuation ${(container as? IrDeclarationWithName)?.fqNameWhenAvailable}")
        )

        irBody.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid()
                if (!expression.isSuspend) {
                    if (expression.symbol == context.ir.symbols.getContinuation)
                        return getContinuation()
                    return expression
                }
                val newFun: IrSimpleFunction =
                    context.mapping.suspendFunctionsToFunctionWithContinuations[expression.symbol.owner] ?: error("No mapping")

                return irCall(expression, newFun.symbol, newReturnType = context.irBuiltIns.anyNType).also {
                    it.putValueArgument(
                        it.valueArgumentsCount - 1, getContinuation()
                    )
                }
            }
        })
    }
}

class AddContinuationLowering(val context: JsCommonBackendContext) : DeclarationContainerLoweringPass {
    override fun lower(irDeclarationContainer: IrDeclarationContainer) {
        irDeclarationContainer.transformDeclarationsFlat {
            if (it is IrSimpleFunction && it.isSuspend) {
                transformToView(it)
            } else {
                null
            }
        }
    }

    private fun transformToView(function: IrSimpleFunction): List<IrSimpleFunction> {
        val view = function.suspendFunctionViewOrStub(context)
        // Using custom mapping because number of parameters doesn't match
        val parameterMapping = function.explicitParameters.zip(view.explicitParameters).toMap()
        view.body = function.moveBodyTo(view, parameterMapping)
        val body = view.body
        if (
            function.returnType == context.irBuiltIns.unitType &&
            body is IrBlockBody &&
            body.statements.lastOrNull() !is IrReturn
        ) {
            body.statements += context.createIrBuilder(view.symbol).irReturnUnit()
        }
        return listOf(view)
    }
}

private fun IrSimpleFunction.suspendFunctionViewOrStub(context: JsCommonBackendContext): IrSimpleFunction {
    return context.mapping.suspendFunctionsToFunctionWithContinuations.getOrPut(this) {
        createSuspendFunctionStub(context)
    }
}

private fun IrSimpleFunction.createSuspendFunctionStub(context: JsCommonBackendContext): IrSimpleFunction {
    require(this.isSuspend)
    return factory.buildFun {
        updateFrom(this@createSuspendFunctionStub)
        name = this@createSuspendFunctionStub.name
        origin = this@createSuspendFunctionStub.origin
        returnType = context.irBuiltIns.anyNType
    }.also { function ->
        function.parent = parent

        function.annotations += annotations
        function.metadata = metadata

        function.copyAttributes(this)
        function.copyTypeParametersFrom(this)
        val substitutionMap = makeTypeParameterSubstitutionMap(this, function)
        function.copyReceiverParametersFrom(this, substitutionMap)

        function.overriddenSymbols += overriddenSymbols.map {
            it.owner.suspendFunctionViewOrStub(context).symbol
        }
        function.valueParameters = valueParameters.map { it.copyTo(function) }

        function.addValueParameter(
            "\$continuation",
            continuationType(context).substitute(substitutionMap),
            IrDeclarationOrigin.CONTINUATION
        )
    }
}

private fun IrFunction.continuationType(context: JsCommonBackendContext): IrType {
    // TODO: Use more concrete type?
    return context.coroutineSymbols.continuationClass.typeWith(returnType)
}