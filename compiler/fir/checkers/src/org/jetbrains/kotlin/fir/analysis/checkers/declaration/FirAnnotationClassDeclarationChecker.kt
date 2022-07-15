/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtNodeTypes.FUN
import org.jetbrains.kotlin.KtNodeTypes.VALUE_PARAMETER
import org.jetbrains.kotlin.descriptors.ClassKind.ANNOTATION_CLASS
import org.jetbrains.kotlin.descriptors.ClassKind.ENUM_CLASS
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.hasValOrVar
import org.jetbrains.kotlin.diagnostics.hasVar
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.*
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors.CYCLE_IN_ANNOTATION_PARAMETER
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.name.StandardClassIds.primitiveArrayTypeByElementType
import org.jetbrains.kotlin.name.StandardClassIds.unsignedArrayTypeByElementType

object FirAnnotationClassDeclarationChecker : FirRegularClassChecker() {
    override fun CheckerContext.check(declaration: FirRegularClass, reporter: DiagnosticReporter) {
        if (declaration.classKind != ANNOTATION_CLASS) return
        if (declaration.isLocal) reporter.reportOn(declaration.source, FirErrors.LOCAL_ANNOTATION_CLASS_ERROR)

        if (declaration.superTypeRefs.size != 1) {
            reporter.reportOn(declaration.source, FirErrors.SUPERTYPES_FOR_ANNOTATION_CLASS)
        }

        for (member in declaration.declarations) {
            withSuppressedDiagnostics(member) {
                checkAnnotationClassMember(member, reporter)
            }
        }

        if (declaration.getRetention() != AnnotationRetention.SOURCE &&
            KotlinTarget.EXPRESSION in declaration.getAllowedAnnotationTargets()
        ) {
            val target = declaration.getRetentionAnnotation() ?: declaration.getTargetAnnotation() ?: declaration
            reporter.reportOnWithSuppression(target, FirErrors.RESTRICTED_RETENTION_FOR_EXPRESSION_ANNOTATION, this)
        }

        checkCyclesInParameters(declaration.symbol, reporter)
    }

    private fun CheckerContext.checkAnnotationClassMember(member: FirDeclaration, reporter: DiagnosticReporter) {
        when {
            member is FirConstructor && member.isPrimary -> {
                for (parameter in member.valueParameters) {
                    val source = parameter.source ?: continue
                    if (!source.hasValOrVar() || source.hasVar()) {
                        reporter.reportOnWithSuppression(
                            parameter,
                            if (!source.hasVar()) FirErrors.MISSING_VAL_ON_ANNOTATION_PARAMETER else FirErrors.VAR_ANNOTATION_PARAMETER,
                            this
                        )
                    }
                    val defaultValue = parameter.defaultValue
                    if (defaultValue != null && checkConstantArguments(defaultValue, session) != null) {
                        reporter.reportOnWithSuppression(
                            defaultValue, FirErrors.ANNOTATION_PARAMETER_DEFAULT_VALUE_MUST_BE_CONSTANT, this
                        )
                    }

                    val typeRef = parameter.returnTypeRef
                    val coneType = typeRef.coneTypeSafe<ConeLookupTagBasedType>() ?: return
                    val classId = coneType.classId

                    when {
                        classId == ClassId.fromString("<error>") -> {
                            // TODO: replace with UNRESOLVED_REFERENCE check
                        }
                        coneType.isNullable -> {
                            reporter.reportOnWithSuppression(typeRef, FirErrors.NULLABLE_TYPE_OF_ANNOTATION_MEMBER, this)
                        }
                        coneType.isPrimitiveOrNullablePrimitive -> {
                            // DO NOTHING: primitives are allowed as annotation class parameter
                        }
                        coneType.isUnsignedTypeOrNullableUnsignedType -> {
                            // TODO: replace with EXPERIMENTAL_UNSIGNED_LITERALS check
                        }
                        classId == StandardClassIds.KClass -> {
                            // DO NOTHING: KClass is allowed
                        }
                        classId == StandardClassIds.String -> {
                            // DO NOTHING: String is allowed
                        }
                        classId != null && classId in primitiveArrayTypeByElementType.values -> {
                            // DO NOTHING: primitive arrays are allowed
                        }
                        classId != null && classId in unsignedArrayTypeByElementType.values -> {
                            // DO NOTHING: arrays of unsigned types are allowed
                        }
                        classId == StandardClassIds.Array -> {
                            if (!isAllowedArray(typeRef, session)) {
                                reporter.reportOnWithSuppression(typeRef, FirErrors.INVALID_TYPE_OF_ANNOTATION_MEMBER, this)
                            }
                        }
                        isAllowedClassKind(coneType, session) -> {
                            // DO NOTHING: annotation or enum classes are allowed
                        }
                        else -> {
                            reporter.reportOnWithSuppression(typeRef, FirErrors.INVALID_TYPE_OF_ANNOTATION_MEMBER, this)
                        }
                    }
                }
            }
            member is FirRegularClass -> {
                // DO NOTHING: nested annotation classes are allowed in 1.3+
            }
            member is FirProperty && member.source?.elementType == VALUE_PARAMETER -> {
                // DO NOTHING to avoid reporting constructor properties
            }
            member is FirSimpleFunction && member.source?.elementType != FUN -> {
                // DO NOTHING to avoid reporting synthetic functions
                // TODO: replace with origin check
            }
            else -> {
                reporter.reportOn(member.source, FirErrors.ANNOTATION_CLASS_MEMBER)
            }
        }
    }

    private fun isAllowedClassKind(cone: ConeLookupTagBasedType, session: FirSession): Boolean {
        val typeRefClassKind = (cone.lookupTag.toSymbol(session) as? FirRegularClassSymbol)
            ?.classKind
            ?: return false

        return typeRefClassKind == ANNOTATION_CLASS || typeRefClassKind == ENUM_CLASS
    }

    private fun isAllowedArray(typeRef: FirTypeRef, session: FirSession): Boolean {
        val typeArguments = typeRef.coneType.typeArguments

        if (typeArguments.size != 1) return false

        val arrayType = (typeArguments[0] as? ConeKotlinTypeProjection)
            ?.type
            ?: return false

        if (arrayType.isNullable) return false

        val arrayTypeClassId = arrayType.classId

        when {
            arrayTypeClassId == StandardClassIds.KClass -> {
                // KClass is allowed
                return true
            }
            arrayTypeClassId == StandardClassIds.String -> {
                // String is allowed
                return true
            }
            isAllowedClassKind(arrayType as ConeLookupTagBasedType, session) -> {
                // annotation or enum classes are allowed
                return true
            }
        }

        return false
    }

    private fun CheckerContext.checkCyclesInParameters(annotation: FirRegularClassSymbol, reporter: DiagnosticReporter) {
        val primaryConstructor = annotation.primaryConstructorSymbol() ?: return
        val checker = CycleChecker(annotation, session)
        for (valueParameter in primaryConstructor.valueParameterSymbols) {
            if (checker.parameterHasCycle(annotation, valueParameter)) {
                reporter.reportOn(valueParameter.source, CYCLE_IN_ANNOTATION_PARAMETER)
            }
        }
    }

    private class CycleChecker(val targetAnnotation: FirRegularClassSymbol, val session: FirSession) {
        private val visitedAnnotations = mutableSetOf(targetAnnotation)
        private val annotationsWithCycle = mutableSetOf(targetAnnotation)

        fun annotationHasCycle(annotation: FirRegularClassSymbol): Boolean {
            val primaryConstructor = annotation.primaryConstructorSymbol() ?: return false
            for (valueParameter in primaryConstructor.valueParameterSymbols) {
                if (parameterHasCycle(annotation, valueParameter)) return true
            }
            return false
        }

        fun parameterHasCycle(ownedAnnotation: FirRegularClassSymbol, parameter: FirValueParameterSymbol): Boolean {
            val returnType = parameter.resolvedReturnTypeRef.coneType
            return when {
                parameter.isVararg || returnType.isNonPrimitiveArray -> false
                returnType.typeArguments.isNotEmpty() -> {
                    if (returnType.classId == StandardClassIds.KClass) return false
                    for (argument in returnType.typeArguments) {
                        if (typeHasCycle(ownedAnnotation, argument.type ?: continue)) return true
                    }
                    false
                }
                else -> typeHasCycle(ownedAnnotation, returnType)
            }
        }

        fun typeHasCycle(ownedAnnotation: FirRegularClassSymbol, type: ConeKotlinType): Boolean {
            val referencedAnnotation = type.fullyExpandedType(session)
                .toRegularClassSymbol(session)
                ?.takeIf { it.classKind == ANNOTATION_CLASS }
                ?: return false
            if (!visitedAnnotations.add(referencedAnnotation)) {
                return (referencedAnnotation in annotationsWithCycle).also {
                    if (it) {
                        annotationsWithCycle += ownedAnnotation
                    }
                }
            }
            if (referencedAnnotation == targetAnnotation) {
                annotationsWithCycle += ownedAnnotation
                return true
            }
            return annotationHasCycle(referencedAnnotation)
        }
    }


    private fun FirRegularClassSymbol.primaryConstructorSymbol(): FirConstructorSymbol? {
        for (declarationSymbol in this.declarationSymbols) {
            if (declarationSymbol is FirConstructorSymbol && declarationSymbol.isPrimary) {
                return declarationSymbol
            }
        }
        return null
    }
}
