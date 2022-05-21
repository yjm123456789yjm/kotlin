/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.konan.diagnostics

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass

object NativeObjCRefinementChecker : DeclarationChecker {
    private val refinesForObjCFqName = FqName("kotlin.native.RefinesForObjC")
    private val refinesInSwiftFqName = FqName("kotlin.native.RefinesInSwift")
    private val supportedTargets = arrayOf(KotlinTarget.FUNCTION, KotlinTarget.PROPERTY)

    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        checkAnnotation(declaration, descriptor, context)
        checkDeclaration(declaration, descriptor, context)
    }

    private fun checkAnnotation(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is ClassDescriptor || descriptor.kind != ClassKind.ANNOTATION_CLASS) return
        val (objCAnnotation, swiftAnnotation) = descriptor.findRefinesAnnotations()
        if (objCAnnotation == null && swiftAnnotation == null) return
        if (objCAnnotation != null && swiftAnnotation != null) {
            val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(swiftAnnotation) ?: declaration
            context.trace.report(ErrorsNative.REDUNDANT_SWIFT_REFINEMENT.on(reportLocation))
        }
        val targets = AnnotationChecker.applicableTargetSet(descriptor)
        val unsupportedTargets = targets - supportedTargets
        if (unsupportedTargets.isNotEmpty()) {
            objCAnnotation?.let { context.trace.reportInvalidAnnotationTargets(declaration, it) }
            swiftAnnotation?.let { context.trace.reportInvalidAnnotationTargets(declaration, it) }
        }
    }

    private fun DeclarationDescriptor.findRefinesAnnotations(): Pair<AnnotationDescriptor?, AnnotationDescriptor?> {
        var objCAnnotation: AnnotationDescriptor? = null
        var swiftAnnotation: AnnotationDescriptor? = null
        for (annotation in annotations) {
            when (annotation.fqName) {
                refinesForObjCFqName -> objCAnnotation = annotation
                refinesInSwiftFqName -> swiftAnnotation = annotation
            }
            if (objCAnnotation != null && swiftAnnotation != null) break
        }
        return objCAnnotation to swiftAnnotation
    }

    private fun BindingTrace.reportInvalidAnnotationTargets(
        declaration: KtDeclaration,
        annotation: AnnotationDescriptor
    ) {
        val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(annotation) ?: declaration
        report(ErrorsNative.INVALID_OBJC_REFINEMENT_TARGETS.on(reportLocation))
    }

    private fun checkDeclaration(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is CallableMemberDescriptor) return
        if (descriptor !is FunctionDescriptor && descriptor !is PropertyDescriptor) return
        val (objCAnnotations, swiftAnnotations) = descriptor.findRefinedAnnotations()
        if (objCAnnotations.isNotEmpty() && swiftAnnotations.isNotEmpty()) {
            swiftAnnotations.forEach {
                val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(it) ?: declaration
                context.trace.report(ErrorsNative.REDUNDANT_SWIFT_REFINEMENT.on(reportLocation))
            }
        }
        checkOverrides(declaration, descriptor, context, objCAnnotations, swiftAnnotations)
    }

    private fun DeclarationDescriptor.findRefinedAnnotations(): Pair<List<AnnotationDescriptor>, List<AnnotationDescriptor>> {
        val objCAnnotations = mutableListOf<AnnotationDescriptor>()
        val swiftAnnotations = mutableListOf<AnnotationDescriptor>()
        for (annotation in annotations) {
            val annotations = annotation.annotationClass?.annotations ?: continue
            for (metaAnnotation in annotations) {
                when (metaAnnotation.fqName) {
                    refinesForObjCFqName -> {
                        objCAnnotations.add(annotation)
                        break
                    }
                    refinesInSwiftFqName -> {
                        swiftAnnotations.add(annotation)
                        break
                    }
                }
            }
        }
        return objCAnnotations to swiftAnnotations
    }

    private fun checkOverrides(
        declaration: KtDeclaration,
        descriptor: CallableMemberDescriptor,
        context: DeclarationCheckerContext,
        objCAnnotations: List<AnnotationDescriptor>,
        swiftAnnotations: List<AnnotationDescriptor>
    ) {
        if (descriptor.overriddenDescriptors.isEmpty()) return
        var isRefinedForObjC = objCAnnotations.isNotEmpty()
        var isRefinedInSwift = swiftAnnotations.isNotEmpty()
        val supersNotRefinedForObjC = mutableListOf<CallableMemberDescriptor>()
        val supersNotRefinedInSwift = mutableListOf<CallableMemberDescriptor>()
        for (overriddenDescriptor in descriptor.overriddenDescriptors) {
            val (superIsRefinedForObjC, superIsRefinedInSwift) = overriddenDescriptor.inheritsRefinedAnnotations()
            if (superIsRefinedForObjC) isRefinedForObjC = true else supersNotRefinedForObjC.add(overriddenDescriptor)
            if (superIsRefinedInSwift) isRefinedInSwift = true else supersNotRefinedInSwift.add(overriddenDescriptor)
        }
        if (isRefinedForObjC && supersNotRefinedForObjC.isNotEmpty()) {
            context.trace.reportIncompatibleOverride(declaration, objCAnnotations, supersNotRefinedForObjC)
        }
        if (isRefinedInSwift && supersNotRefinedInSwift.isNotEmpty()) {
            context.trace.reportIncompatibleOverride(declaration, swiftAnnotations, supersNotRefinedInSwift)
        }
    }

    private fun CallableMemberDescriptor.inheritsRefinedAnnotations(): Pair<Boolean, Boolean> {
        var (hasObjC, hasSwift) = hasRefinedAnnotations()
        if (hasObjC && hasSwift) return true to true
        for (descriptor in overriddenDescriptors) {
            val (isRefinedForObjC, isRefinedInSwift) = descriptor.inheritsRefinedAnnotations()
            hasObjC = hasObjC || isRefinedForObjC
            hasSwift = hasSwift || isRefinedInSwift
            if (hasObjC && hasSwift) return true to true
        }
        return hasObjC to hasSwift
    }

    private fun CallableMemberDescriptor.hasRefinedAnnotations(): Pair<Boolean, Boolean> {
        var hasObjC = false
        var hasSwift = false
        for (annotation in annotations) {
            val annotations = annotation.annotationClass?.annotations ?: continue
            for (metaAnnotation in annotations) {
                when (metaAnnotation.fqName) {
                    refinesForObjCFqName -> {
                        hasObjC = true
                        break
                    }
                    refinesInSwiftFqName -> {
                        hasSwift = true
                        break
                    }
                }
            }
            if (hasObjC && hasSwift) return true to true
        }
        return hasObjC to hasSwift
    }

    private fun BindingTrace.reportIncompatibleOverride(
        declaration: KtDeclaration,
        annotations: List<AnnotationDescriptor>,
        notRefinedSupers: List<CallableMemberDescriptor>
    ) {
        val containingDeclarations = notRefinedSupers.map { it.containingDeclaration }
        if (annotations.isEmpty()) {
            report(ErrorsNative.INCOMPATIBLE_OBJC_REFINEMENT_OVERRIDE.on(declaration, containingDeclarations))
        } else {
            annotations.forEach {
                val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(it) ?: declaration
                report(ErrorsNative.INCOMPATIBLE_OBJC_REFINEMENT_OVERRIDE.on(reportLocation, containingDeclarations))
            }
        }
    }
}