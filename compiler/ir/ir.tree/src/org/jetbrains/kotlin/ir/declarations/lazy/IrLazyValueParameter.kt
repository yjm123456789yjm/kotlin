/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations.lazy

import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.DeclarationStubGenerator
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.name.Name

@OptIn(ObsoleteDescriptorBasedAPI::class)
class IrLazyValueParameter(
    override val startOffset: Int,
    override val endOffset: Int,
    override var origin: IrDeclarationOrigin,
    override val symbol: IrValueParameterSymbol,
    override val descriptor: ValueParameterDescriptor,
    override val name: Name,
    override val index: Int,
    override var type: IrType,
    override var varargElementType: IrType?,
    isCrossinline: Boolean,
    isNoinline: Boolean,
    isHidden: Boolean,
    isAssignable: Boolean,
    override val stubGenerator: DeclarationStubGenerator,
    override val typeTranslator: TypeTranslator,
) : IrValueParameter(), IrLazyDeclarationBase {
    override lateinit var parent: IrDeclarationParent

    override var defaultValue: IrExpressionBody? = null

    override var annotations: List<IrConstructorCall> by createLazyAnnotations()

    private val flags = collectFlags(
        isCrossinline = isCrossinline,
        isNoinline = isNoinline,
        isHidden = isHidden,
        isAssignable = isAssignable
    )

    private fun getFlag(mask: Int) = (flags and mask) != 0

    override val isAssignable: Boolean
        get() = getFlag(IS_ASSIGNABLE)
    override val isCrossinline: Boolean
        get() = getFlag(IS_CROSSINLINE)
    override val isNoinline: Boolean
        get() = getFlag(IS_NOINLINE)
    override val isHidden: Boolean
        get() = getFlag(IS_HIDDEN)

    init {
        symbol.bind(this)
    }
}