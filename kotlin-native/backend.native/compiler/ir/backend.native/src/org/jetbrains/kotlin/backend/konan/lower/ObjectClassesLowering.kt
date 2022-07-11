/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.IrBuildingTransformer
import org.jetbrains.kotlin.backend.common.lower.at
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.descriptors.synthesizedName
import org.jetbrains.kotlin.backend.konan.ir.isUnit
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrUninitializedType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource

internal class ObjectClassesLowering(val context: Context) : FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.transform(object: IrBuildingTransformer(context) {
            override fun visitClass(declaration: IrClass) : IrDeclaration {
                super.visitClass(declaration)
                if (declaration.isObject && !declaration.isUnit()) {
                    processObjectClass(declaration)
                }
                return declaration
            }

            override fun visitGetObjectValue(expression: IrGetObjectValue) : IrExpression {
                super.visitGetObjectValue(expression)
                if (expression.symbol.owner.isUnit()) {
                    return expression
                }
                return builder.at(expression).irCall(getObjectClassInstanceFunction(expression.symbol))
            }
        }, null)
    }

    fun processObjectClass(declaration: IrClass) {
        val symbols = context.ir.symbols
        val primaryConstructor = declaration.constructors.single { it.isPrimary }
        require(primaryConstructor.valueParameters.isEmpty())
        val instanceField = declaration.addField {
            name = instanceFieldName
            isFinal = true
            isStatic = true
            type = declaration.defaultType
        }.also { field ->
            val builder = context.createIrBuilder(field.symbol, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET)
            field.initializer = builder.irExprBody(
                    builder.irBlock {
                        +irSetField(null, field, irCall(symbols.createUninitializedInstance, listOf(declaration.defaultType)))
                        +irCall(symbols.initInstance).apply {
                            putValueArgument(0, irGetField(null, field))
                            putValueArgument(1, irCallConstructor(primaryConstructor.symbol, emptyList()))
                        }
                        +irGetField(null, field)
                    }
            )
        }
        val function = getObjectClassInstanceFunction(declaration.symbol)
        declaration.declarations.add(function)
        function.body = context.createIrBuilder(function.symbol, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET).irBlockBody {
            +irReturn(irGetField(null, instanceField))
        }
    }

    fun getObjectClassInstanceFunction(symbol: IrClassSymbol) = context.objectInstanceMethods.getOrPut(symbol) {
        context.irFactory.buildFun {
            name = instanceFunctionName
            returnType = symbol.defaultType
        }.apply {
            parent = symbol.owner
        }
    }

    companion object {
        val instanceFunctionName = "instance".synthesizedName
        val instanceFieldName = "instanceField".synthesizedName
    }

}