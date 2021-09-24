/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.backend.konan.lower

import org.jetbrains.kotlin.backend.common.lower.PropertyDelegationLowering
import org.jetbrains.kotlin.backend.common.lower.PropertyDelegationLowering.KTypeGeneratorInterface
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.ir.buildSimpleAnnotation
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType

internal class KonanPropertyDelegationLowering(val backendContext: Context) : PropertyDelegationLowering(backendContext) {
    override fun IrField.posProcessKPropertiesField() {
        annotations += buildSimpleAnnotation(context.irBuiltIns, startOffset, endOffset, backendContext.ir.symbols.sharedImmutable.owner)
        annotations += buildSimpleAnnotation(context.irBuiltIns, startOffset, endOffset, backendContext.ir.symbols.eagerInitialization.owner)
    }

    override fun createKTypeGenerator(irFile: IrFile, expression: IrExpression): KTypeGeneratorInterface {
        return KTypeGeneratorInterface { type: IrType -> with(KTypeGenerator(backendContext, irFile, expression)) { irKType(type) } }
    }
}
