/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.intrinsics

import org.jetbrains.kotlin.backend.jvm.codegen.*
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrRawFunctionReference
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Type

object JvmSerializedLambdaEquals : IntrinsicMethod() {
    override fun invoke(expression: IrFunctionAccessExpression, codegen: ExpressionCodegen, data: BlockInfo): PromisedValue {
        val samType = expression.getTypeArgument(0) as? IrSimpleType
            ?: throw AssertionError("Simple type expected in type argument #0: ${expression.dump()}")
        val samClass = samType.classOrNull
            ?: throw AssertionError("SAM type should be a class type: ${samType.render()}")
        val samTypeInternalName = codegen.typeMapper.mapClass(samClass.owner).internalName

        val arg0 = expression.getValueArgument(0) as? IrGetValue
            ?: throw AssertionError("IrGetValue expected in argument #0: ${expression.dump()}")
        val varIndex = codegen.frameMap.getIndex(arg0.symbol)
        if (varIndex < 0)
            throw AssertionError("Unmapped variable: ${arg0.render()}")

        val arg1 = expression.getValueArgument(1) as? IrRawFunctionReference
            ?: throw AssertionError("IrRawFunctionReference expected in argument #0: ${expression.dump()}")
        val samFun = arg1.symbol.owner
        val samMethodName = codegen.methodSignatureMapper.mapFunctionName(samFun)
        val samMethod = codegen.methodSignatureMapper.mapAsmMethod(samFun)

        val arg2 = expression.getValueArgument(2) as? IrRawFunctionReference
            ?: throw AssertionError("IrRawFunctionReference expected in argument #2: ${expression.dump()}")
        val implMethodHandle = JvmInvokeDynamic.generateMethodHandle(arg2, codegen)

        return object : BooleanValue(codegen) {
            override fun discard() {
            }

            override fun jumpIfFalse(target: Label) {
                codegen.mv.run {
                    load(varIndex, SERIALIZED_LAMBDA_TYPE)
                    invokevirtual(SERIALIZED_LAMBDA, "getImplMethodKind", "()I", false)
                    iconst(implMethodHandle.tag)
                    ificmpne(target)

                    load(varIndex, SERIALIZED_LAMBDA_TYPE)
                    invokevirtual(SERIALIZED_LAMBDA, "getFunctionalInterfaceClass", "()Ljava/lang/String;", false)
                    aconst(samTypeInternalName)
                    invokevirtual(OBJECT, "equals", "(Ljava/lang/Object;)Z", false)
                    ifeq(target)

                    load(varIndex, SERIALIZED_LAMBDA_TYPE)
                    invokevirtual(SERIALIZED_LAMBDA, "getFunctionalInterfaceMethodName", "()Ljava/lang/String;", false)
                    aconst(samMethodName)
                    invokevirtual(OBJECT, "equals", "(Ljava/lang/Object;)Z", false)
                    ifeq(target)

                    load(varIndex, SERIALIZED_LAMBDA_TYPE)
                    invokevirtual(SERIALIZED_LAMBDA, "getFunctionalInterfaceMethodSignature", "()Ljava/lang/String;", false)
                    aconst(samMethod.descriptor)
                    invokevirtual(OBJECT, "equals", "(Ljava/lang/Object;)Z", false)
                    ifeq(target)

                    load(varIndex, SERIALIZED_LAMBDA_TYPE)
                    invokevirtual(SERIALIZED_LAMBDA, "getImplClass", "()Ljava/lang/String;", false)
                    aconst(implMethodHandle.owner)
                    invokevirtual(OBJECT, "equals", "(Ljava/lang/Object;)Z", false)
                    ifeq(target)

                    load(varIndex, SERIALIZED_LAMBDA_TYPE)
                    invokevirtual(SERIALIZED_LAMBDA, "getImplMethodSignature", "()Ljava/lang/String;", false)
                    aconst(implMethodHandle.desc)
                    invokevirtual(OBJECT, "equals", "(Ljava/lang/Object;)Z", false)
                    ifeq(target)

                    println("samTypeInternalName: $samTypeInternalName")
                    println("samMethodName: $samMethodName")
                    println("samMethodType.descriptor: ${samMethod.descriptor}")
                    println("implMethodHandled: $implMethodHandle")
                }
            }

            override fun jumpIfTrue(target: Label) {
                codegen.mv.run {
                    val falseLabel = Label()
                    jumpIfFalse(falseLabel)
                    goTo(target)
                    mark(falseLabel)
                }
            }
        }
    }

    private val OBJECT = AsmTypes.OBJECT_TYPE.internalName
    private const val SERIALIZED_LAMBDA = "java/lang/invoke/SerializedLambda"
    private val SERIALIZED_LAMBDA_TYPE = Type.getObjectType(SERIALIZED_LAMBDA)
}
