/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.hasValOrVar
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.analysis.checkers.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.findClosest
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.analysis.diagnostics.reportOnWithSuppression
import org.jetbrains.kotlin.fir.analysis.diagnostics.withSuppressedDiagnostics
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.hasBackingField
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeSafe
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds

object FirAnnotationChecker : FirBasicDeclarationChecker() {
    private val deprecatedClassId = FqName("kotlin.Deprecated")
    private val deprecatedSinceKotlinClassId = FqName("kotlin.DeprecatedSinceKotlin")

    override fun CheckerContext.check(
        declaration: FirDeclaration,
        reporter: DiagnosticReporter
    ) {
        var deprecated: FirAnnotation? = null
        var deprecatedSinceKotlin: FirAnnotation? = null

        for (annotation in declaration.annotations) {
            val fqName = annotation.fqName(session) ?: continue
            if (fqName == deprecatedClassId) {
                deprecated = annotation
            } else if (fqName == deprecatedSinceKotlinClassId) {
                deprecatedSinceKotlin = annotation
            }

            withSuppressedDiagnostics(annotation) {
                checkAnnotationTarget(declaration, annotation, reporter)
            }
        }
        if (deprecatedSinceKotlin != null) {
            withSuppressedDiagnostics(deprecatedSinceKotlin) {
                checkDeprecatedCalls(deprecatedSinceKotlin, deprecated, reporter)
            }
        }

        checkRepeatedAnnotations(declaration, this, reporter)

        if (declaration is FirProperty) {
            this.checkRepeatedAnnotationsInProperty(declaration, reporter)
        } else if (declaration is FirCallableDeclaration) {
            if (declaration.source?.kind !is KtFakeSourceElementKind) {
                withSuppressedDiagnostics(declaration.returnTypeRef) {
                    checkRepeatedAnnotationsInType(declaration.returnTypeRef.coneTypeSafe(), reporter)
                }
            }
        } else if (declaration is FirTypeAlias) {
            withSuppressedDiagnostics(declaration.expandedTypeRef) {
                checkRepeatedAnnotationsInType(declaration.expandedTypeRef.coneType, reporter)
            }
        }
    }

    private fun CheckerContext.checkAnnotationTarget(
        declaration: FirDeclaration,
        annotation: FirAnnotation,
        reporter: DiagnosticReporter
    ) {
        val actualTargets = getActualTargetList(declaration)
        val applicableTargets = annotation.getAllowedAnnotationTargets(session)
        val useSiteTarget = annotation.useSiteTarget

        fun check(targets: List<KotlinTarget>) = targets.any {
            it in applicableTargets && (useSiteTarget == null || KotlinTarget.USE_SITE_MAPPING[useSiteTarget] == it)
        }

        fun checkWithUseSiteTargets(): Boolean {
            if (useSiteTarget == null) return false

            val useSiteMapping = KotlinTarget.USE_SITE_MAPPING[useSiteTarget]
            return actualTargets.onlyWithUseSiteTarget.any { it in applicableTargets && it == useSiteMapping }
        }

        if (useSiteTarget != null) {
            this.checkAnnotationUseSiteTarget(declaration, annotation, useSiteTarget, reporter)
        }

        if (check(actualTargets.defaultTargets) || check(actualTargets.canBeSubstituted) || checkWithUseSiteTargets()) {
            return
        }

        val targetDescription = actualTargets.defaultTargets.firstOrNull()?.description ?: "unidentified target"
        if (useSiteTarget != null) {
            reporter.reportOn(
                annotation.source,
                FirErrors.WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET,
                targetDescription,
                useSiteTarget.renderName
            )
        } else {
            if (declaration is FirProperty && declaration.source?.kind == KtFakeSourceElementKind.PropertyFromParameter) return
            reporter.reportOn(
                annotation.source,
                FirErrors.WRONG_ANNOTATION_TARGET,
                targetDescription
            )
        }
    }

    private fun CheckerContext.checkAnnotationUseSiteTarget(
        annotated: FirDeclaration,
        annotation: FirAnnotation,
        target: AnnotationUseSiteTarget,
        reporter: DiagnosticReporter
    ) {
        if (annotation.source?.kind == KtFakeSourceElementKind.FromUseSiteTarget) return
        when (target) {
            AnnotationUseSiteTarget.PROPERTY,
            AnnotationUseSiteTarget.PROPERTY_GETTER -> {
            }
            AnnotationUseSiteTarget.FIELD -> {
                if (annotated is FirProperty && annotated.delegateFieldSymbol != null && !annotated.hasBackingField) {
                    reporter.reportOn(annotation.source, FirErrors.INAPPLICABLE_TARGET_PROPERTY_HAS_NO_BACKING_FIELD)
                }
            }
            AnnotationUseSiteTarget.PROPERTY_DELEGATE_FIELD -> {
                if (annotated is FirProperty && annotated.delegateFieldSymbol == null) {
                    reporter.reportOn(annotation.source, FirErrors.INAPPLICABLE_TARGET_PROPERTY_HAS_NO_DELEGATE)
                }
            }
            AnnotationUseSiteTarget.PROPERTY_SETTER,
            AnnotationUseSiteTarget.SETTER_PARAMETER -> {
                if (annotated !is FirProperty || annotated.isLocal) {
                    reporter.reportOn(annotation.source, FirErrors.INAPPLICABLE_TARGET_ON_PROPERTY, target.renderName)
                } else if (!annotated.isVar) {
                    reporter.reportOn(annotation.source, FirErrors.INAPPLICABLE_TARGET_PROPERTY_IMMUTABLE, target.renderName)
                }
            }
            AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER -> when {
                annotated is FirValueParameter -> {
                    val container = containingDeclarations.lastOrNull()
                    if (container is FirConstructor && container.isPrimary) {
                        if (annotated.source?.hasValOrVar() != true) {
                            reporter.reportOn(annotation.source, FirErrors.REDUNDANT_ANNOTATION_TARGET, target.renderName)
                        }
                    } else {
                        reporter.reportOn(annotation.source, FirErrors.INAPPLICABLE_PARAM_TARGET)
                    }
                }
                annotated is FirProperty && annotated.source?.kind == KtFakeSourceElementKind.PropertyFromParameter -> {
                }
                else -> reporter.reportOn(annotation.source, FirErrors.INAPPLICABLE_PARAM_TARGET)
            }
            AnnotationUseSiteTarget.FILE -> {
                // NB: report once?
                if (annotated !is FirFile) {
                    reporter.reportOn(annotation.source, FirErrors.INAPPLICABLE_FILE_TARGET)
                }
            }
            AnnotationUseSiteTarget.RECEIVER -> {
                // NB: report once?
                // annotation with use-site target `receiver` can be only on type reference, but not on declaration
                reporter.reportOn(
                    annotation.source, FirErrors.WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET, "declaration", target.renderName
                )
            }
        }
    }

    private fun CheckerContext.checkDeprecatedCalls(
        deprecatedSinceKotlin: FirAnnotation,
        deprecated: FirAnnotation?,
        reporter: DiagnosticReporter
    ) {
        val closestFirFile = findClosest<FirFile>()
        if (closestFirFile != null && !closestFirFile.packageFqName.startsWith(StandardClassIds.BASE_KOTLIN_PACKAGE.shortName())) {
            reporter.reportOn(
                deprecatedSinceKotlin.source,
                FirErrors.DEPRECATED_SINCE_KOTLIN_OUTSIDE_KOTLIN_SUBPACKAGE
            )
        }

        if (deprecated == null) {
            reporter.reportOn(deprecatedSinceKotlin.source, FirErrors.DEPRECATED_SINCE_KOTLIN_WITHOUT_DEPRECATED)
        } else {
            val argumentMapping = deprecated.argumentMapping.mapping
            for (name in argumentMapping.keys) {
                if (name.identifier == "level") {
                    reporter.reportOn(
                        deprecatedSinceKotlin.source,
                        FirErrors.DEPRECATED_SINCE_KOTLIN_WITH_DEPRECATED_LEVEL
                    )
                    break
                }
            }
        }
    }

    private fun checkRepeatedAnnotations(
        annotationContainer: FirAnnotationContainer,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        context.checkRepeatedAnnotation(annotationContainer, annotationContainer.annotations, reporter)
    }

    private fun CheckerContext.checkRepeatedAnnotationsInType(
        type: ConeKotlinType?,
        reporter: DiagnosticReporter
    ) {
        if (type == null) return
        val fullyExpandedType = type.fullyExpandedType(session)
        checkRepeatedAnnotation(null, fullyExpandedType.attributes.customAnnotations, reporter)
        for (typeArgument in fullyExpandedType.typeArguments) {
            if (typeArgument is ConeKotlinType) {
                checkRepeatedAnnotationsInType(typeArgument, reporter)
            }
        }
    }

    private fun CheckerContext.checkRepeatedAnnotationsInProperty(
        property: FirProperty,
        reporter: DiagnosticReporter
    ) {
        fun FirAnnotationContainer?.getAnnotationTypes(): List<ConeKotlinType> {
            return this?.annotations?.map { it.annotationTypeRef.coneType }.orEmpty()
        }

        val propertyAnnotations = mapOf(
            AnnotationUseSiteTarget.PROPERTY_GETTER to property.getter?.getAnnotationTypes(),
            AnnotationUseSiteTarget.PROPERTY_SETTER to property.setter?.getAnnotationTypes(),
            AnnotationUseSiteTarget.SETTER_PARAMETER to property.setter?.valueParameters?.single().getAnnotationTypes()
        )

        val isError = session.languageVersionSettings.supportsFeature(LanguageFeature.ProhibitRepeatedUseSiteTargetAnnotations)

        for (annotation in property.annotations) {
            val useSiteTarget = annotation.useSiteTarget ?: property.getDefaultUseSiteTarget(annotation, this)
            val existingAnnotations = propertyAnnotations[useSiteTarget] ?: continue

            if (annotation.annotationTypeRef.coneType in existingAnnotations && !annotation.isRepeatable(session)) {
                val factory = if (isError) FirErrors.REPEATED_ANNOTATION else FirErrors.REPEATED_ANNOTATION_WARNING
                if (annotation.source?.kind !is KtFakeSourceElementKind) {
                    reporter.reportOnWithSuppression(annotation, factory, this)
                }
            }
        }
    }
}

