/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.ir.declarations.impl

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.name.Name

open class IrClassImpl(
    override val startOffset: Int,
    override val endOffset: Int,
    override var origin: IrDeclarationOrigin,
    final override val symbol: IrClassSymbol,
    override val name: Name,
    override val kind: ClassKind,
    override var visibility: DescriptorVisibility,
    override var modality: Modality,
    isCompanion: Boolean = false,
    isInner: Boolean = false,
    isData: Boolean = false,
    isExternal: Boolean = false,
    isInline: Boolean = false,
    isExpect: Boolean = false,
    isFun: Boolean = false,
    override val source: SourceElement = SourceElement.NO_SOURCE,
    override val factory: IrFactory = IrFactoryImpl
) : IrClass() {
    init {
        symbol.bind(this)
    }

    override lateinit var parent: IrDeclarationParent
    override var annotations: List<IrConstructorCall> = emptyList()

    @ObsoleteDescriptorBasedAPI
    override val descriptor: ClassDescriptor
        get() = symbol.descriptor

    override var thisReceiver: IrValueParameter? = null

    override val declarations: MutableList<IrDeclaration> = ArrayList()

    override var typeParameters: List<IrTypeParameter> = emptyList()

    override var superTypes: List<IrType> = emptyList()

    override var inlineClassRepresentation: InlineClassRepresentation<IrSimpleType>? = null

    override var metadata: MetadataSource? = null

    override var attributeOwnerId: IrAttributeContainer = this

    override var sealedSubclasses: List<IrClassSymbol> = emptyList()

    private var flags =
        collectFlags(
            isCompanion = isCompanion,
            isInner = isInner,
            isData = isData,
            isExternal = isExternal,
            isInline = isInline,
            isExpect = isExpect,
            isFun = isFun
        )

    private fun getFlag(mask: Int) = (flags and mask) != 0

    private fun setFlag(mask: Int, value: Boolean) {
        flags = if (value) {
            flags or mask
        } else {
            flags and mask.inv()
        }
    }

    override val isCompanion: Boolean
        get() = getFlag(IS_COMPANION)
    override var isInner: Boolean
        get() = getFlag(IS_INNER)
        set(v) {
            setFlag(IS_INNER, v)
        }
    override val isData: Boolean
        get() = getFlag(IS_DATA)
    override val isInline: Boolean
        get() = getFlag(IS_INLINE)
    override val isExpect: Boolean
        get() = getFlag(IS_EXPECT)
    override val isFun: Boolean
        get() = getFlag(IS_FUN)
    override val isExternal: Boolean
        get() = getFlag(IS_EXTERNAL)

}
