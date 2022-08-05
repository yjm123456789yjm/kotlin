/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.interfaces

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.utils.isJsReflectedClass
import org.jetbrains.kotlin.ir.backend.js.utils.isJsSubtypeCheckable
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

private const val GET_KCLASS_INTERFACE_POSITION = 0
private const val IS_INTERFACE_INTERFACE_POSITION = 1

class CollectInterfacesForInstanceCheckLowering(val context: JsIrBackendContext) : BodyLoweringPass {
    private val transformer = TypeCheckableInterfacesCollector(context)

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.transformChildrenVoid(transformer)
    }

    private class TypeCheckableInterfacesCollector(val context: JsIrBackendContext) : IrElementTransformerVoid() {
        private val jsSubtypeCheckableCtor = context.intrinsics.jsSubtypeCheckableAnnotationSymbol.constructors.single()
        private val jsReflectedClassAnnotationCtor = context.intrinsics.jsReflectedClassAnnotationSymbol.constructors.single()

        override fun visitCall(expression: IrCall): IrExpression {
            when (expression.symbol) {
                context.intrinsics.isInterfaceSymbol ->
                    expression.getInterfaceClassDeclaration()?.annotateAsJsTypeCheckable()

                context.reflectionSymbols.getKClass ->
                    expression.getInterfaceClassDeclaration()?.apply {
                        annotateAsJsTypeCheckable()
                        annotateAsJsReflectedClass()
                    }
            }

            return super.visitCall(expression)

        }

        private fun IrCall.getInterfaceClassDeclaration(): IrClass? {
            val interfacePosition = when (symbol) {
                context.intrinsics.isInterfaceSymbol -> IS_INTERFACE_INTERFACE_POSITION
                context.reflectionSymbols.getKClass -> GET_KCLASS_INTERFACE_POSITION
                else -> return null
            }

            var interfaceReference = getValueArgument(interfacePosition) as? IrCall ?: return null

            if (interfaceReference.symbol == context.intrinsics.getInterfaceIdInRuntimeSymbol) {
                interfaceReference = interfaceReference.getValueArgument(0) as IrCall
            }

            val interfaceType = interfaceReference.takeIf { it.typeArgumentsCount > 0 }?.getTypeArgument(0)
            val classSymbol = interfaceType?.classifierOrNull as? IrClassSymbol

            return classSymbol?.owner?.takeIf { it.isInterface }
        }

        private fun IrClass.annotateAsJsReflectedClass() {
            if (!isJsReflectedClass()) {
                annotations += context.createIrBuilder(symbol).irCall(jsReflectedClassAnnotationCtor)
            }
        }

        private fun IrClass.annotateAsJsTypeCheckable() {
            if (!isJsSubtypeCheckable()) {
                annotations += context.createIrBuilder(symbol).irCall(jsSubtypeCheckableCtor)
            }
        }
    }
}