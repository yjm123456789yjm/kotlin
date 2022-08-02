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
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName

class JsDefaultArgumentStubGenerator(override val context: JsIrBackendContext) :
    DefaultArgumentStubGenerator(context, skipExternalMethods = true, forceSetOverrideSymbols = false) {
    private val void = context.intrinsics.void

    private fun IrBuilderWithScope.createDefaultResolutionExpression(
        fromParameter: IrValueParameter,
        toParameter: IrValueParameter,
    ): IrExpression? {
        return fromParameter.defaultValue?.let { defaultValue ->
            irIfThenElse(
                toParameter.type,
                irEqeqeq(
                    irGet(toParameter, context.irBuiltIns.anyNType),
                    irGetField(null, void.owner.backingField!!)
                ),
                defaultValue.expression,
                irGet(toParameter)
            )
        }
    }

    private fun IrConstructor.introduceDefaultResolution(): IrConstructor {
        val irBuilder = context.createIrBuilder(symbol, startOffset, endOffset)

        val variables = mutableMapOf<IrValueParameter, IrValueDeclaration>()

        val defaultResolutionStatements = valueParameters.mapNotNull { valueParameter ->
            irBuilder.createDefaultResolutionExpression(valueParameter, valueParameter)?.let { initializer ->
                JsIrBuilder.buildVar(
                    valueParameter.type,
                    this@introduceDefaultResolution,
                    name = valueParameter.name.asString(),
                    initializer = initializer
                ).also {
                    variables[valueParameter] = it
                }
            }
        }

        if (variables.isNotEmpty()) {
            body?.transformChildren(VariableRemapper(variables), null)

            body = context.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET) {
                statements += defaultResolutionStatements
                statements += body?.statements ?: emptyList()
            }
        }


        return also {
            valueParameters.forEach {
                if (it.defaultValue != null) {
                    it.origin = JsLoweredDeclarationOrigin.JS_SHADOWED_DEFAULT_PARAMETER
                }
            }
            // Hack to insert undefined values inside default parameters
            context.mapping.defaultArgumentsDispatchFunction[it] = it
        }
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

        defaultFunStub.typeParameters = emptyList()
        defaultFunStub.valueParameters = emptyList()
        defaultFunStub.copyParameterDeclarationsFrom(originalFun)

        if (!defaultFunStub.isFakeOverride) {
            with(defaultFunStub) {
                body = generateSpecializedForJsDefaultStubBody(originalFun)

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

    private fun IrFunction.generateSpecializedForJsDefaultStubBody(originalDeclaration: IrFunction): IrBody {
        val irBuilder = context.createIrBuilder(symbol, startOffset, endOffset)

        return irBuilder.irBlockBody(this) {
            +irReturn(irCall(originalDeclaration).apply {
                passTypeArgumentsFrom(originalDeclaration)
                dispatchReceiver = dispatchReceiverParameter?.let { irGet(it) }
                extensionReceiver = extensionReceiverParameter?.let { irGet(it) }

                originalDeclaration.valueParameters.forEachIndexed { index, irValueParameter ->
                    val exportedParameter = valueParameters[index]
                    val value = createDefaultResolutionExpression(irValueParameter, exportedParameter) ?: irGet(exportedParameter)
                    putValueArgument(index, value)
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