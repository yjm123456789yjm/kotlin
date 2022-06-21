/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi2ir.generators

import org.jetbrains.kotlin.backend.common.CodegenUtil
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.addMember
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.declareSimpleFunctionWithOverrides

class ObjectClassMembersGenerator(declarationGenerator: DeclarationGenerator) : DeclarationGeneratorExtension(declarationGenerator) {
    fun generateSpecialMembers(irClass: IrClass) {
        generateToString(irClass)
    }

    private fun generateToString(irClass: IrClass) {
        val function = CodegenUtil.getMemberToGenerate(
            irClass.descriptor, "toString",
            KotlinBuiltIns::isString, List<ValueParameterDescriptor>::isEmpty
        ) ?: return

        irClass.addMember(
            context.symbolTable.declareSimpleFunctionWithOverrides(
                SYNTHETIC_OFFSET, SYNTHETIC_OFFSET,
                IrDeclarationOrigin.OBJECT_TO_STRING,
                function
            ).buildWithScope { irFunction ->
                FunctionGenerator(declarationGenerator).generateFunctionParameterDeclarationsAndReturnType(
                    irFunction,
                    null,
                    null,
                    emptyList()
                )
                irFunction.body = context.irFactory.createExpressionBody(
                    IrConstImpl.string(
                        SYNTHETIC_OFFSET,
                        SYNTHETIC_OFFSET,
                        context.irBuiltIns.stringType,
                        irClass.name.asString()
                    )
                )
            }
        )
    }
}