/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.JsLoweredDeclarationOrigin
import org.jetbrains.kotlin.ir.backend.js.export.isExported
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.backend.js.utils.JsAnnotations
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName

class JsDefaultArgumentStubGenerator(override val context: JsIrBackendContext) :
    DefaultArgumentStubGenerator(context, skipExternalMethods = true, forceSetOverrideSymbols = false, keepOriginalArguments = true) {
    private val void = context.intrinsics.void

    private fun IrBuilderWithScope.createDefaultResolutionExpression(
        defaultExpression: IrExpression?,
        toParameter: IrValueParameter,
    ): IrExpression? {
        return defaultExpression?.let {
            irIfThenElse(
                toParameter.type,
                irEqeqeqWithoutBox(
                    irGet(toParameter, toParameter.type),
                    irGetField(null, void.owner.backingField!!, toParameter.type)
                ),
                it,
                irGet(toParameter)
            )
        }
    }

    private fun IrBuilderWithScope.createResolutionStatement(
        parameter: IrValueParameter,
        defaultExpression: IrExpression?,
    ): IrSetValue? {
        return createDefaultResolutionExpression(defaultExpression, parameter)?.let {
            JsIrBuilder.buildSetValue(parameter.symbol, it)
        }
    }

    private fun IrConstructor.introduceDefaultResolution(): IrConstructor {
        val irBuilder = context.createIrBuilder(symbol, startOffset, endOffset)

        val variables = mutableMapOf<IrValueParameter, IrValueParameter>()

        valueParameters = valueParameters.map { param ->
            param.takeIf { it.defaultValue != null }
                ?.copyTo(this, isAssignable = true, origin = JsLoweredDeclarationOrigin.JS_SHADOWED_DEFAULT_PARAMETER)
                ?.also { new -> variables[param] = new } ?: param
        }

        val defaultResolutionStatements = valueParameters.mapNotNull {
            irBuilder.createResolutionStatement(it, it.defaultValue?.expression)
        }

        if (variables.isNotEmpty()) {
            body?.transformChildren(VariableRemapper(variables), null)

            body = context.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET) {
                statements += defaultResolutionStatements
                statements += body?.statements ?: emptyList()
            }

            context.mapping.defaultArgumentsDispatchFunction[this] = this
        }

        return this
    }

    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (declaration !is IrFunction || declaration.isExternalOrInheritedFromExternal()) {
            return null
        }

        if (declaration is IrConstructor && declaration.hasDefaultArgs()) {
            return listOf(declaration.introduceDefaultResolution())
        }

        val (originalFun, defaultFunStub) = super.transformFlat(declaration) ?: return null

        if (originalFun !is IrFunction || defaultFunStub !is IrFunction) {
            return listOf(originalFun, defaultFunStub)
        }

        if (!defaultFunStub.isFakeOverride) {
            with(defaultFunStub) {
                valueParameters.forEach {
                    if (it.defaultValue != null) {
                        it.origin = JsLoweredDeclarationOrigin.JS_SHADOWED_DEFAULT_PARAMETER
                    }
                    it.defaultValue = null
                }

                if (originalFun.isExported(context)) {
                    context.additionalExportedDeclarations.add(defaultFunStub)

                    if (!originalFun.hasAnnotation(JsAnnotations.jsNameFqn)) {
                        annotations += originalFun.generateJsNameAnnotationCall()
                    }
                }
            }
        }

        val (exportAnnotations, irrelevantAnnotations) = originalFun.annotations
            .map { it.deepCopyWithSymbols(originalFun as? IrDeclarationParent) }
            .partition {
                it.isAnnotation(JsAnnotations.jsExportFqn) || (it.isAnnotation(JsAnnotations.jsNameFqn))
            }

        originalFun.annotations = irrelevantAnnotations
        defaultFunStub.annotations += exportAnnotations
        originalFun.origin = JsLoweredDeclarationOrigin.JS_SHADOWED_EXPORT

        return listOf(originalFun, defaultFunStub)
    }

    override fun IrFunction.generateDefaultStubBody(originalDeclaration: IrFunction): IrBody {
        val irBuilder = context.createIrBuilder(symbol, startOffset, endOffset)

        val variables = mutableMapOf<IrValueParameter, IrValueDeclaration>().apply {
            originalDeclaration.dispatchReceiverParameter?.let {
                set(it, dispatchReceiverParameter!!)
            }
            originalDeclaration.extensionReceiverParameter?.let {
                set(it, extensionReceiverParameter!!)
            }
            originalDeclaration.valueParameters.forEachIndexed { index, param ->
                set(param, valueParameters[index])
            }
        }

        return irBuilder.irBlockBody(this) {
            +valueParameters.zip(originalDeclaration.valueParameters)
                .mapNotNull { (new, original) ->
                    createResolutionStatement(
                        new,
                        original.defaultValue?.expression?.transform(VariableRemapper(variables), null),
                    )
                }

            +irReturn(irCall(originalDeclaration).apply {
                passTypeArgumentsFrom(originalDeclaration)
                dispatchReceiver = dispatchReceiverParameter?.let { irGet(it) }
                extensionReceiver = extensionReceiverParameter?.let { irGet(it) }

                originalDeclaration.valueParameters.forEachIndexed { index, irValueParameter ->
                    putValueArgument(index, irGet(variables[irValueParameter] ?: valueParameters[index]))
                }
            })
        }
    }

    private fun IrFunction.generateJsNameAnnotationCall(): IrConstructorCall {
        val builder = context.createIrBuilder(symbol, startOffset, endOffset)

        return with(context) {
            builder.irCall(intrinsics.jsNameAnnotationSymbol.constructors.single())
                .apply {
                    putValueArgument(
                        0,
                        IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.stringType, name.identifier)
                    )
                }
        }
    }

    private fun IrConstructorCall.isAnnotation(name: FqName): Boolean {
        return symbol.owner.parentAsClass.fqNameWhenAvailable == name
    }

    private fun IrFunction.hasDefaultArgs(): Boolean =
        valueParameters.any { it.defaultValue != null }
}