/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.konan.diagnostics

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass

object NativeObjCRefinementChecker : DeclarationChecker {
    private val refinesForObjCFqName = FqName("kotlin.native.RefinesForObjC")
    private val refinesInSwiftFqName = FqName("kotlin.native.RefinesInSwift")

    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        checkAnnotation(declaration, descriptor, context)
        checkDeclaration(declaration, descriptor, context)
    }

    private fun checkAnnotation(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is ClassDescriptor || descriptor.kind != ClassKind.ANNOTATION_CLASS) return
        val (refinesForObjC, refinesInSwift) = descriptor.findRefinesAnnotations()
        if (refinesForObjC == null && refinesInSwift == null) return
        if (refinesForObjC != null && refinesInSwift != null) {
            val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(refinesInSwift) ?: declaration
            context.trace.report(ErrorsNative.REDUNDANT_SWIFT_REFINEMENT.on(reportLocation))
        }
        // TODO: Check annotation targets
    }

    private fun checkDeclaration(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is FunctionDescriptor && descriptor !is PropertyDescriptor) return
        val (refinedForObjC, refinedInSwift) = descriptor.findRefinedAnnotations()
        val isRefinedForObjC = refinedForObjC.isNotEmpty()
        val isRefinedInSwift = refinedInSwift.isNotEmpty()
        if (!isRefinedForObjC && !isRefinedInSwift) return
        if (isRefinedForObjC && isRefinedInSwift) {
            refinedInSwift.forEach {
                val reportLocation = DescriptorToSourceUtils.getSourceFromAnnotation(it) ?: declaration
                context.trace.report(ErrorsNative.REDUNDANT_SWIFT_REFINEMENT.on(reportLocation))
            }
        }
        // TODO: Check overrides
    }

    private fun DeclarationDescriptor.findRefinesAnnotations(): Pair<AnnotationDescriptor?, AnnotationDescriptor?> {
        var refinesForObjC: AnnotationDescriptor? = null
        var refinesInSwift: AnnotationDescriptor? = null
        for (annotation in annotations) {
            when (annotation.fqName) {
                refinesForObjCFqName -> refinesForObjC = annotation
                refinesInSwiftFqName -> refinesInSwift = annotation
            }
            if (refinesForObjC != null && refinesInSwift != null) break
        }
        return refinesForObjC to refinesInSwift
    }

    private fun DeclarationDescriptor.findRefinedAnnotations(): Pair<List<AnnotationDescriptor>, List<AnnotationDescriptor>> {
        val refinedForObjC = mutableListOf<AnnotationDescriptor>()
        val refinedInSwift = mutableListOf<AnnotationDescriptor>()
        for (annotation in annotations) {
            val annotations = annotation.annotationClass?.annotations ?: continue
            for (metaAnnotation in annotations) {
                when (metaAnnotation.fqName) {
                    refinesForObjCFqName -> {
                        refinedForObjC.add(annotation)
                        break
                    }
                    refinesInSwiftFqName -> {
                        refinedInSwift.add(annotation)
                        break
                    }
                }
            }
        }
        return refinedForObjC to refinedInSwift
    }
}