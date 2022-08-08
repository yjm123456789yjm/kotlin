/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.interfaces

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.utils.Namer
import org.jetbrains.kotlin.ir.backend.js.utils.getJsSubtypeCheckableInterfaces
import org.jetbrains.kotlin.ir.backend.js.utils.isJsReflectedClass
import org.jetbrains.kotlin.ir.backend.js.utils.isJsSubtypeCheckable
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDynamicMemberExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

private const val IS_INTERFACE_INTERFACE_POSITION = 1
private const val REFERENCE_BUILDERS_SUPERTYPES_POSITION = 2
private const val LOCAL_DELEGATE_BUILDER_SUPERTYPES_POSITION = 1

class SubstituteTypeCheckableInterfacesLowering(context: JsIrBackendContext) : BodyLoweringPass {
    private val transformer = SubstituteTypeCheckableInterfacesTransformer(context)

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.transformChildrenVoid(transformer)
    }

    private class SubstituteTypeCheckableInterfacesTransformer(private val context: JsIrBackendContext) : IrElementTransformerVoid() {
        private val isInterfaceSymbol = context.intrinsics.isInterfaceSymbol
        private val referenceBuilderSymbol = context.kpropertyBuilder
        private val localDelegateBuilderSymbol = context.klocalDelegateBuilder

        private val arrayOfSymbol get() = context.irBuiltIns.arrayOf
        private val jsClassSymbol get() = context.intrinsics.jsClass
        private val getInterfaceIdSymbol get() = context.intrinsics.getInterfaceIdSymbol

        override fun visitCall(expression: IrCall): IrExpression {
            with(expression) {
                when (symbol) {
                    isInterfaceSymbol -> putValueArgument(IS_INTERFACE_INTERFACE_POSITION, getInterfaceId())
                    referenceBuilderSymbol -> putValueArgument(REFERENCE_BUILDERS_SUPERTYPES_POSITION, getJsSuperTypes())
                    localDelegateBuilderSymbol -> putValueArgument(LOCAL_DELEGATE_BUILDER_SUPERTYPES_POSITION, getJsSuperTypes())
                }
            }

            return super.visitCall(expression)
        }

        private fun IrCall.getInterfaceId(): IrExpression {
            val jsClassCall = getValueArgument(IS_INTERFACE_INTERFACE_POSITION) as IrCall
            if (jsClassCall.symbol != jsClassSymbol) return jsClassCall
            val interfaceDeclaration = jsClassCall.getTypeArgument(0)?.classOrNull?.owner ?: error("Unexpected usage of jsClass intrinsic")
            return interfaceDeclaration.getJsTypeConstructor(jsClassCall)
        }

        private fun IrCall.getJsSuperTypes(): IrExpression {
            val listOfClasses = IrCallImpl(startOffset, endOffset, arrayOfSymbol.owner.returnType, arrayOfSymbol, 0, 1)

            val position = when (symbol) {
                referenceBuilderSymbol -> REFERENCE_BUILDERS_SUPERTYPES_POSITION
                localDelegateBuilderSymbol -> LOCAL_DELEGATE_BUILDER_SUPERTYPES_POSITION
                else -> error("Unexpected call provided")
            }

            val jsClassCall = (getValueArgument(position) as? IrCall)?.takeIf { it.symbol == jsClassSymbol }
                ?: error("Unexpected way to provide type class. Expect to have jsClassSymbol call")

            val classDeclaration = jsClassCall.getTypeArgument(0)?.classOrNull?.owner
                ?: error("Unexpected way to provide class type as an argument to jsClassSymbol call")

            val subtypableInterfaces = classDeclaration.getJsSubtypeCheckableInterfaces()?.toMutableList() ?: mutableListOf()

            if (classDeclaration.isInterface && classDeclaration.isJsSubtypeCheckable()) {
                subtypableInterfaces.add(
                    IrClassReferenceImpl(
                        startOffset,
                        endOffset,
                        context.dynamicType,
                        classDeclaration.symbol,
                        context.dynamicType
                    )
                )
            }

            val subtypableInterfacesAsVararg = IrVarargImpl(
                startOffset,
                endOffset,
                context.dynamicType,
                context.dynamicType,
                subtypableInterfaces.map { it.getJsTypeConstructor() }
            )

            return listOfClasses.apply {
                putValueArgument(0, subtypableInterfacesAsVararg)
            }
        }

        private fun IrClassReference.getJsTypeConstructor(): IrExpression {
            val irCall = IrCallImpl(startOffset, endOffset, jsClassSymbol.owner.returnType, jsClassSymbol, 1, 0)
                .also { it.putTypeArgument(0, symbol.defaultType) }

            val declaration = symbol.owner as? IrClass ?: return irCall
            return declaration.getJsTypeConstructor(irCall)
        }

        private fun IrClass.getJsTypeConstructor(jsClassCall: IrCall): IrExpression {
            return when {
                isJsReflectedClass() -> IrCallImpl(
                    startOffset,
                    endOffset,
                    context.irBuiltIns.intType,
                    getInterfaceIdSymbol,
                    0,
                    1,
                ).apply { putValueArgument(0, jsClassCall) }

                else -> jsClassCall
            }
        }
    }
}