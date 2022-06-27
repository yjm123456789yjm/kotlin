/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.ir.createJvmIrBuilder
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fields
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name

internal val readResolveForDataObjectsPhase = makeIrFilePhase(
    ::ReadResolveForDataObjectsLowering,
    name = "ReadResolveForDataObjectsLowering",
    description = "Generate readResole for data objects"
)

private class ReadResolveForDataObjectsLowering(val context: JvmBackendContext) : FileLoweringPass {
    override fun lower(irFile: IrFile) {
        if (!context.state.languageVersionSettings.supportsFeature(LanguageFeature.DataObjects)) return
        irFile.transform(ReadResolveForDataObjectsLoweringTransformer(context), null)
    }
}

class ReadResolveForDataObjectsLoweringTransformer(private val context: JvmBackendContext) : IrElementTransformerVoid() {
    override fun visitClass(declaration: IrClass): IrStatement {
        if (declaration.isData && declaration.kind == ClassKind.OBJECT && declaration.isSerializable()) {
            context.irFactory.buildFun {
                name = Name.identifier("readResolve")
                modality = Modality.FINAL
                origin = IrDeclarationOrigin.GENERATED_DATA_CLASS_MEMBER
                returnType = context.irBuiltIns.anyType
                visibility = DescriptorVisibilities.PRIVATE
            }.apply {
                addDispatchReceiver { type = declaration.defaultType }
                parent = declaration
                body = context.createJvmIrBuilder(symbol)
                    .run { irExprBody(irGetField(null, declaration.fields.find { it.name.asString() == JvmAbi.INSTANCE_FIELD }!!)) }
                declaration.declarations.add(this)
            }
        }
        return super.visitClass(declaration)
    }
}

private fun IrClass.isSerializable(): Boolean {
    fun IrClass.isSerializable(alreadyVisited: MutableSet<IrClass>): Boolean =
        (this.fqNameWhenAvailable?.asString() == "java.io.Serializable")
                || (alreadyVisited.add(this) && superTypes.any { ((it as? IrSimpleType)?.classifier as? IrClassSymbol)?.owner?.isSerializable() == true })

    return this.isSerializable(mutableSetOf())
}