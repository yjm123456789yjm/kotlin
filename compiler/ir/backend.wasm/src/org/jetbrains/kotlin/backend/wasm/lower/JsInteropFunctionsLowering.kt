/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.lower

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.DeclarationTransformer
import org.jetbrains.kotlin.backend.common.ir.createStaticFunctionWithReceivers
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.isBuiltInWasmRefType
import org.jetbrains.kotlin.backend.wasm.ir2wasm.isExported
import org.jetbrains.kotlin.backend.wasm.ir2wasm.isExternalType
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.utils.getJsNameOrKotlinName
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

/**
 * Create wrappers for external and @JsExport functions when type adaptation is needed
 */
class JsInteropFunctionsLowering(val context: WasmBackendContext) : DeclarationTransformer {
    val builtIns = context.irBuiltIns
    val symbols = context.wasmSymbols
    val adapters = symbols.jsInteropAdapters

    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (declaration.isFakeOverride) return null
        if (declaration !is IrSimpleFunction) return null
        val isExported = declaration.isExported()
        val isExternal = declaration.isExternal
        if (!isExported && !isExternal) return null
        require(!(isExported && isExternal)) { "Exported external declarations are not supported" }
        return if (isExternal)
            transformExternalFunction(declaration)
        else
            transformExportFunction(declaration)
    }

    fun transformExternalFunction(function: IrSimpleFunction): List<IrDeclaration>? {
        val valueParametersAdapters = function.valueParameters.map {
            it.type.needToAdaptKotlinToJs(isReturn = false)
        }
        val resultAdapter =
            function.returnType.needToAdaptJsToKotlin(isReturn = true)

        if (resultAdapter == null && valueParametersAdapters.all { it == null })
            return null

        // Create new function
        val newFun = context.irFactory.createStaticFunctionWithReceivers(
            function.parent,
            name = Name.identifier(function.name.asStringStripSpecialMarkers() + "__externalAdapter"),
            function
        )

        // Change old external function signature
        function.valueParameters.forEachIndexed { index, newParameter ->
            val adapter = valueParametersAdapters[index]
            if (adapter != null) {
                newParameter.type = adapter.toType
            }
        }
        resultAdapter?.let {
            function.returnType = resultAdapter.fromType
        }

        // Delegate new function to old function:
        val builder = context.createIrBuilder(newFun.symbol)
        newFun.body = builder.irBlockBody {
            +irReturn(
                irCall(function).let { call ->
                    for ((index, valueParameter) in newFun.valueParameters.withIndex()) {
                        val get = irGet(valueParameter)
                        call.putValueArgument(index, valueParametersAdapters[index]?.adapt(get, builder) ?: get)
                    }
                    resultAdapter?.adapt(call, builder) ?: call
                }
            )
        }
        newFun.annotations = emptyList()

        context.mapping.wasmJsInteropFunctionToWrapper[function] = newFun
        return listOf(function, newFun)
    }

    /**
     *  @JsExport fun foo(x: KotlinType): KotlinType { <original-body> }
     *
     *  ->
     *
     *  @JsExport fun foo(x: JsType): JsType =
     *      return adapt(foo(adapt(x)));
     *
     *  fun foo__JsExportWrapper(x: KotlinType): KotlinType { <original-body> }
     */
    fun transformExportFunction(function: IrSimpleFunction): List<IrDeclaration>? {
        val valueParametersAdapters = function.valueParameters.map {
            it.type.needToAdaptJsToKotlin(isReturn = false)
        }
        val resultAdapter =
            function.returnType.needToAdaptKotlinToJs(isReturn = true)

        if (resultAdapter == null && valueParametersAdapters.all { it == null })
            return null

        // Create new function
        val newFun = context.irFactory.createStaticFunctionWithReceivers(
            function.parent,
            name = Name.identifier(function.name.asStringStripSpecialMarkers() + "__JsExportImpl"),
            function
        )

        // Change old exported function signature
        newFun.valueParameters.forEachIndexed { index, newParameter ->
            val adapter = valueParametersAdapters[index]
            if (adapter != null) {
                newParameter.type = adapter.fromType
            }
        }
        resultAdapter?.let {
            newFun.returnType = resultAdapter.toType
        }

        // Delegate new function to old function:
        val builder = context.createIrBuilder(newFun.symbol)
        newFun.body = builder.irBlockBody {
            +irReturn(
                irCall(function).let { call ->
                    for ((index, valueParameter) in newFun.valueParameters.withIndex()) {
                        val get = irGet(valueParameter)
                        call.putValueArgument(index, valueParametersAdapters[index]?.adapt(get, builder) ?: get)
                    }
                    resultAdapter?.adapt(call, builder) ?: call
                }
            )
        }

        newFun.annotations += builder.irCallConstructor(context.wasmSymbols.jsNameConstructor, typeArguments = emptyList()).also {
            it.putValueArgument(0, builder.irString(function.getJsNameOrKotlinName().identifier))
        }
        function.annotations = function.annotations.filter { it.symbol != context.wasmSymbols.jsExportConstructor }

        context.mapping.wasmJsInteropFunctionToWrapper[function] = newFun
        return listOf(function, newFun)
    }

    private fun IrType.needToAdaptKotlinToJs(isReturn: Boolean): Adapter? {
        if (isReturn && this == builtIns.unitType)
            return null

        when (this) {
            builtIns.stringType -> return FunctionBasedAdapter(adapters.kotlinToJsStringAdapter.owner)
            builtIns.stringType.makeNullable() -> return NullOrAdapter(FunctionBasedAdapter(adapters.kotlinToJsStringAdapter.owner))
            builtIns.booleanType -> return FunctionBasedAdapter(adapters.kotlinToJsBooleanAdapter.owner)
            builtIns.anyType -> return FunctionBasedAdapter(adapters.kotlinToJsAnyAdapter.owner)

            builtIns.byteType,
            builtIns.shortType,
            builtIns.charType,
            builtIns.intType,
            builtIns.longType,
            builtIns.floatType,
            builtIns.doubleType,
            context.wasmSymbols.voidType ->
                return null

        }

        if (isExternalType(this))
            return null

        if (isBuiltInWasmRefType(this))
            return null

        return SendKotlinObjectToJsAdapter(this)
    }

    private fun IrType.needToAdaptJsToKotlin(isReturn: Boolean): Adapter? {
        if (isReturn && this == builtIns.unitType)
            return null

        when (this) {
            builtIns.stringType -> return FunctionBasedAdapter(adapters.jsToKotlinStringAdapter.owner)
            builtIns.anyType -> return FunctionBasedAdapter(adapters.jsToKotlinAnyAdapter.owner)
            builtIns.byteType -> return FunctionBasedAdapter(adapters.jsToKotlinByteAdapter.owner)
            builtIns.shortType -> return FunctionBasedAdapter(adapters.jsToKotlinShortAdapter.owner)
            builtIns.charType -> return FunctionBasedAdapter(adapters.jsToKotlinCharAdapter.owner)

            builtIns.booleanType,
            builtIns.intType,
            builtIns.longType,
            builtIns.floatType,
            builtIns.doubleType,
            context.wasmSymbols.voidType ->
                return null
        }

        if (isExternalType(this))
            return null

        if (isBuiltInWasmRefType(this))
            return null

        return ReceivingKotlinObjectFromJsAdapter(this)
    }

    interface Adapter {
        val fromType: IrType
        val toType: IrType
        fun adapt(expression: IrExpression, builder: IrBuilderWithScope): IrExpression
    }

    class FunctionBasedAdapter(
        private val function: IrSimpleFunction,
    ) : Adapter {
        override val fromType = function.valueParameters[0].type
        override val toType = function.returnType
        override fun adapt(expression: IrExpression, builder: IrBuilderWithScope): IrExpression {
            val call = IrCallImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                function.returnType,
                function.symbol,
                valueArgumentsCount = 1,
                typeArgumentsCount = 0,
            )
            call.putValueArgument(0, expression)
            return call
        }
    }

    inner class SendKotlinObjectToJsAdapter(
        override val fromType: IrType
    ) : Adapter {
        override val toType: IrType = context.wasmSymbols.wasmDataRefType
        override fun adapt(expression: IrExpression, builder: IrBuilderWithScope): IrExpression {
            return IrTypeOperatorCallImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                toType,
                IrTypeOperator.REINTERPRET_CAST,
                toType,
                expression
            )
        }
    }

    inner class ReceivingKotlinObjectFromJsAdapter(
        override val toType: IrType
    ) : Adapter {
        override val fromType: IrType = context.wasmSymbols.wasmDataRefType
        override fun adapt(expression: IrExpression, builder: IrBuilderWithScope): IrExpression {
            val call = IrCallImpl(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                toType,
                context.wasmSymbols.wasmRefCast,
                valueArgumentsCount = 1,
                typeArgumentsCount = 1,
            )
            call.putValueArgument(0, expression)
            call.putTypeArgument(0, toType)
            return call
        }
    }

    inner class NullOrAdapter(
        val adapter: Adapter
    ) : Adapter {
        override val fromType: IrType = adapter.fromType.makeNullable()
        override val toType: IrType = adapter.toType.makeNullable()
        override fun adapt(expression: IrExpression, builder: IrBuilderWithScope): IrExpression {
            return builder.irComposite {
                val tmp = irTemporary(adapter.adapt(expression, builder))
                +irIfNull(toType, irGet(tmp), irNull(toType), irImplicitCast(irGet(tmp), toType))
            }
        }
    }
}

/**
 * Redirect calls to external and @JsExport functions to created wrappers
 */
class JsInteropFunctionCallsLowering(val context: WasmBackendContext) : BodyLoweringPass {
    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid()
                val newFun: IrSimpleFunction? = context.mapping.wasmJsInteropFunctionToWrapper[expression.symbol.owner]
                return if (newFun != null && container != newFun) {
                    irCall(expression, newFun)
                } else {
                    expression
                }
            }
        })
    }

}