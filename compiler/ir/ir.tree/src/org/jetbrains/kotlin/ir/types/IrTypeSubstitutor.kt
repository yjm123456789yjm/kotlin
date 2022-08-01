/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.types

import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.isSingleFieldValueClass
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrScriptSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.impl.*
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.superTypes
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.asSimpleType
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.asTypeArgument
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.getType
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isStarProjection
import org.jetbrains.kotlin.types.model.TypeSubstitutorMarker


abstract class AbstractIrTypeSubstitutor(private val irBuiltIns: IrBuiltIns) : TypeSubstitutorMarker {

    private fun IrType.typeParameterConstructor(): IrTypeParameterSymbol? {
        return if (this is IrSimpleType) classifier as? IrTypeParameterSymbol
        else null
    }

    abstract fun getSubstitutionArgument(typeParameter: IrTypeParameterSymbol): IrTypeArgument

    abstract fun isEmptySubstitution(): Boolean

    fun substitute(type: IrType): IrType {
        if (isEmptySubstitution()) return type

        return type.typeParameterConstructor()?.let {
            when (val typeArgument = getSubstitutionArgument(it)) {
                is IrStarProjection -> it.owner.superTypes.firstOrNull()?.copyNullabilityFrom(type) ?: irBuiltIns.anyNType
                is IrTypeProjection -> typeArgument.type.copyNullabilityFrom(type)
                else -> error("unknown type argument")
            }
        } ?: substituteType(type)
    }

    private fun IrType.copyNullabilityFrom(other: IrType): IrType =
        if (other.isNullable()) makeNullable() else this

    private fun substituteType(irType: IrType): IrType {
        return when (irType) {
            is IrSimpleType -> {
                with(irType.toBuilder()) {
                    arguments = irType.arguments.map { substituteTypeArgument(it) }
                    buildSimpleType()
                }
            }
            is IrDynamicType,
            is IrErrorType ->
                irType
            else ->
                throw AssertionError("Unexpected type: $irType")
        }
    }

    private fun substituteTypeArgument(typeArgument: IrTypeArgument): IrTypeArgument {
        if (typeArgument is IrStarProjection) return typeArgument

        require(typeArgument is IrTypeProjection)

        val type = typeArgument.type
        if (type is IrSimpleType) {
            val classifier = type.classifier
            if (classifier is IrTypeParameterSymbol) {
                val newArgument = getSubstitutionArgument(classifier)
                return if (newArgument is IrTypeProjection) {
                    makeTypeProjection(newArgument.type, typeArgument.variance)
                } else newArgument
            }
        }
        return makeTypeProjection(substituteType(typeArgument.type), typeArgument.variance)
    }
}


class IrTypeSubstitutor(
    typeParameters: List<IrTypeParameterSymbol>,
    typeArguments: List<IrTypeArgument>,
    irBuiltIns: IrBuiltIns
) : AbstractIrTypeSubstitutor(irBuiltIns) {

    init {
        assert(typeParameters.size == typeArguments.size) {
            "Unexpected number of type arguments: ${typeArguments.size}\n" +
                    "Type parameters are:\n" +
                    typeParameters.joinToString(separator = "\n") { it.owner.render() } +
                    "Type arguments are:\n" +
                    typeArguments.joinToString(separator = "\n") { it.render() }
        }
    }

    private val substitution = typeParameters.zip(typeArguments).toMap()

    override fun getSubstitutionArgument(typeParameter: IrTypeParameterSymbol): IrTypeArgument =
        substitution[typeParameter]
            ?: throw AssertionError("Unsubstituted type parameter: ${typeParameter.owner.render()}")

    override fun isEmptySubstitution(): Boolean = substitution.isEmpty()
}

class IrCapturedTypeSubstitutor(
    typeParameters: List<IrTypeParameterSymbol>,
    typeArguments: List<IrTypeArgument>,
    capturedTypes: List<IrCapturedType?>,
    irBuiltIns: IrBuiltIns
) : AbstractIrTypeSubstitutor(irBuiltIns) {

    init {
        assert(typeArguments.size == typeParameters.size)
        assert(capturedTypes.size == typeParameters.size)
    }

    private val oldSubstitution = typeParameters.zip(typeArguments).toMap()
    private val capturedSubstitution = typeParameters.zip(capturedTypes).toMap()

    override fun getSubstitutionArgument(typeParameter: IrTypeParameterSymbol): IrTypeArgument {
        return capturedSubstitution[typeParameter]?.let { makeTypeProjection(it, Variance.INVARIANT) }
            ?: oldSubstitution[typeParameter]
            ?: throw AssertionError("Unsubstituted type parameter: ${typeParameter.owner.render()}")
    }

    override fun isEmptySubstitution(): Boolean = oldSubstitution.isEmpty()
}