/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations

import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor

abstract class IrSimpleFunction :
    IrFunction(),
    IrOverridableDeclaration<IrSimpleFunctionSymbol>,
    IrAttributeContainer {

    abstract override val symbol: IrSimpleFunctionSymbol

    abstract val isTailrec: Boolean
    abstract val isSuspend: Boolean
    abstract override val isFakeOverride: Boolean
    abstract val isOperator: Boolean
    abstract val isInfix: Boolean

    abstract var correspondingPropertySymbol: IrPropertySymbol?

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitSimpleFunction(this, data)

    companion object {
        const val IS_INLINE = 0x0000_0000_0000_0001
        const val IS_EXTERNAL = 0x0000_0000_0000_0002
        const val IS_TAILREC = 0x0000_0000_0000_0004
        const val IS_SUSPEND = 0x0000_0000_0000_0008
        const val IS_OPERATOR = 0x0000_0000_0000_0010
        const val IS_INFIX = 0x0000_0000_0000_0020
        const val IS_EXPECT = 0x0000_0000_0000_0040
        const val IS_FAKE_OVERRIDE = 0x0000_0000_0000_0080

        fun collectFlags(
            isInline: Boolean,
            isExternal: Boolean,
            isTailrec: Boolean,
            isSuspend: Boolean,
            isOperator: Boolean,
            isInfix: Boolean,
            isExpect: Boolean,
            isFakeOverride: Boolean
        ): Int {
            var result = 0
            if (isInline) result += IS_INLINE
            if (isExternal) result += IS_EXTERNAL
            if (isTailrec) result += IS_TAILREC
            if (isSuspend) result += IS_SUSPEND
            if (isOperator) result += IS_OPERATOR
            if (isInfix) result += IS_INFIX
            if (isExpect) result += IS_EXPECT
            if (isFakeOverride) result += IS_FAKE_OVERRIDE
            return result
        }
    }
}

val IrFunction.isPropertyAccessor: Boolean
    get() = this is IrSimpleFunction && correspondingPropertySymbol != null
