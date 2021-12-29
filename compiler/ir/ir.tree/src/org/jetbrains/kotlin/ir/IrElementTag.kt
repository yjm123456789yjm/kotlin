/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.ir

import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*

open class IrElementTag {
    class TypeTag<T : IrElement> : IrElementTag()

    companion object {
        val CUSTOM_ELEMENT = IrElementTag()

        val MODULE_FRAGMENT = TypeTag<IrModuleFragment>()
        val FILE = TypeTag<IrFile>()
        val EXTERNAL_PACKAGE_FRAGMENT = TypeTag<IrExternalPackageFragment>()
        val SCRIPT = TypeTag<IrScript>()
        val CLASS = TypeTag<IrClass>()
        val SIMPLE_FUNCTION = TypeTag<IrSimpleFunction>()
        val CONSTRUCTOR = TypeTag<IrConstructor>()
        val PROPERTY = TypeTag<IrProperty>()
        val FIELD = TypeTag<IrField>()
        val LOCAL_DELEGATED_PROPERTY = TypeTag<IrLocalDelegatedProperty>()
        val VARIABLE = TypeTag<IrVariable>()
        val ENUM_ENTRY = TypeTag<IrEnumEntry>()
        val ANONYMOUS_INITIALIZER = TypeTag<IrAnonymousInitializer>()
        val TYPE_PARAMETER = TypeTag<IrTypeParameter>()
        val VALUE_PARAMETER = TypeTag<IrValueParameter>()
        val TYPE_ALIAS = TypeTag<IrTypeAlias>()
        val EXPRESSION_BODY = TypeTag<IrExpressionBody>()
        val BLOCK_BODY = TypeTag<IrBlockBody>()
        val SYNTHETIC_BODY = TypeTag<IrSyntheticBody>()
        val SUSPENDABLE_EXPRESSION = TypeTag<IrSuspendableExpression>()
        val SUSPENSION_POINT = TypeTag<IrSuspensionPoint>()
        val CONST = TypeTag<IrConst<*>>()
        val CONSTANT_OBJECT = TypeTag<IrConstantObject>()
        val CONSTANT_PRIMITIVE = TypeTag<IrConstantPrimitive>()
        val CONSTANT_ARRAY = TypeTag<IrConstantArray>()
        val VARARG = TypeTag<IrVararg>()
        val SPREAD_ELEMENT = TypeTag<IrSpreadElement>()
        val BLOCK = TypeTag<IrBlock>()
        val COMPOSITE = TypeTag<IrComposite>()
        val STRING_CONCATENATION = TypeTag<IrStringConcatenation>()
        val GET_OBJECT_VALUE = TypeTag<IrGetObjectValue>()
        val GET_ENUM_VALUE = TypeTag<IrGetEnumValue>()
        val GET_VALUE = TypeTag<IrGetValue>()
        val SET_VALUE = TypeTag<IrSetValue>()
        val GET_FIELD = TypeTag<IrGetField>()
        val SET_FIELD = TypeTag<IrSetField>()
        val GET_CLASS = TypeTag<IrGetClass>()
        val CALL = TypeTag<IrCall>()
        val CONSTRUCTOR_CALL = TypeTag<IrConstructorCall>()
        val DELEGATING_CONSTRUCTOR_CALL = TypeTag<IrDelegatingConstructorCall>()
        val ENUM_CONSTRUCTOR_CALL = TypeTag<IrEnumConstructorCall>()
        val FUNCTION_REFERENCE = TypeTag<IrFunctionReference>()
        val PROPERTY_REFERENCE = TypeTag<IrPropertyReference>()
        val LOCAL_DELEGATED_PROPERTY_REFERENCE = TypeTag<IrLocalDelegatedPropertyReference>()
        val RAW_FUNCTION_REFERENCE = TypeTag<IrRawFunctionReference>()
        val FUNCTION_EXPRESSION = TypeTag<IrFunctionExpression>()
        val CLASS_REFERENCE = TypeTag<IrClassReference>()
        val INSTANCE_INITIALIZER_CALL = TypeTag<IrInstanceInitializerCall>()
        val TYPE_OPERATOR_CALL = TypeTag<IrTypeOperatorCall>()
        val WHEN = TypeTag<IrWhen>()
        val BRANCH = TypeTag<IrBranch>()
        val ELSE_BRANCH = TypeTag<IrElseBranch>()
        val WHILE_LOOP = TypeTag<IrWhileLoop>()
        val DO_WHILE_LOOP = TypeTag<IrDoWhileLoop>()
        val TRY = TypeTag<IrTry>()
        val CATCH = TypeTag<IrCatch>()
        val BREAK = TypeTag<IrBreak>()
        val CONTINUE = TypeTag<IrContinue>()
        val RETURN = TypeTag<IrReturn>()
        val THROW = TypeTag<IrThrow>()
        val DYNAMIC_OPERATOR_EXPRESSION = TypeTag<IrDynamicOperatorExpression>()
        val DYNAMIC_MEMBER_EXPRESSION = TypeTag<IrDynamicMemberExpression>()
        val ERROR_DECLARATION = TypeTag<IrErrorDeclaration>()
        val ERROR_EXPRESSION = TypeTag<IrErrorExpression>()
        val ERROR_CALL_EXPRESSION = TypeTag<IrErrorCallExpression>()
    }

}

fun IrElement.isa(tag: IrElementTag): Boolean =
    this.tag === tag

@Suppress("UNCHECKED_CAST")
fun <T : IrElement> IrElement.safeAs(tag: IrElementTag.TypeTag<T>): T? =
    if (this.isa(tag)) this as T else null

@Suppress("UNCHECKED_CAST")
fun <T : IrElement> Sequence<IrElement>.filterIs(tag: IrElementTag.TypeTag<T>): Sequence<T> =
    this.filter { it.isa(tag) } as Sequence<T>

