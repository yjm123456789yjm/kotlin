/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.interpreter

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.interpreter.state.Primitive
import org.jetbrains.kotlin.ir.interpreter.state.State
import org.jetbrains.kotlin.ir.interpreter.state.asBoolean
import org.jetbrains.kotlin.ir.interpreter.state.reflection.KPropertyState
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isImmutable
import org.jetbrains.kotlin.ir.util.parentClassOrNull

class OptimizerPrototype(irBuiltIns: IrBuiltIns) : PartialIrInterpreter(irBuiltIns) {
    private fun IrSimpleFunction.hasSideEffect(): Boolean {
        if (correspondingPropertySymbol?.owner?.getter?.origin == IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR) return false
        val type = parentClassOrNull?.defaultType ?: return true
        if (type.isPrimitiveType() || type.isArrayOrPrimitiveArray()) return false
        return true
    }

    private fun IrExpression.convertToConstIfPossible(): IrExpression {
        fun State?.toConstIfPossible(): IrConst<*>? {
            if (this !is Primitive<*> || this.type.isArrayOrPrimitiveArray()) return null
            return this.value.toIrConst(this.type, this@convertToConstIfPossible.startOffset, this@convertToConstIfPossible.endOffset)
        }

        val state = evaluator.callStack.peekState()
        return when (this) {
            is IrBreakContinue -> this
            is IrCall -> {
                if (this.symbol.owner.hasSideEffect()) return this
                state.toConstIfPossible() ?: this
            }
            is IrGetValue, is IrStringConcatenation -> state.toConstIfPossible() ?: this
            else -> this
        }
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val owner = expression.symbol.owner
        if (owner.name.asString() != "invoke") {
            return super.visitCall(expression).convertToConstIfPossible()
        }

        val dispatchState = evaluator.evalIrCallDispatchReceiver(expression)
        if (dispatchState is KPropertyState) {
            // transform invoke call into call to field
            val property = dispatchState.property
            val getterCall = IrCallImpl.fromSymbolOwner(expression.startOffset, expression.endOffset, expression.type, property.getter!!.symbol)
            getterCall.dispatchReceiver = expression.getValueArgument(0)
            return getterCall
        }

        return evaluator.fallbackIrCall(
            expression,
            dispatchState,
            evaluator.evalIrCallExtensionReceiver(expression),
            evaluator.evalIrCallArgs(expression)
        ).convertToConstIfPossible()
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        val result = evaluator.evalIrReturnValue(expression) ?: return evaluator.fallbackIrReturn(expression, null)
        evaluator.callStack.pushState(result)
        expression.value = expression.value.convertToConstIfPossible()
        return evaluator.fallbackIrReturn(expression, null)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        for ((i, branch) in expression.branches.withIndex()) {
            val condition = evaluator.evalIrBranchCondition(branch)
            when {
                condition == null -> return evaluator.fallbackIrWhen(expression, i, inclusive = false)
                condition.asBoolean() -> {
                    evaluator.evalIrBranchResult(branch)?.let { evaluator.callStack.pushState(it) }
                    return branch.result
                }
                // else -> ignore
            }
        }
        return expression
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        val newExpression = super.visitGetValue(expression)
        if (!expression.symbol.owner.isImmutable) return newExpression
        return newExpression.convertToConstIfPossible()
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        return super.visitStringConcatenation(expression).convertToConstIfPossible()
    }
}