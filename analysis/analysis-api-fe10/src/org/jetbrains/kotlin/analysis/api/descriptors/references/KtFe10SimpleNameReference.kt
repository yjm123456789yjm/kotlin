/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.references

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade.AnalysisMode
import org.jetbrains.kotlin.analysis.api.descriptors.KtFe10AnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.references.base.CliKtFe10Reference
import org.jetbrains.kotlin.analysis.api.descriptors.references.base.KtFe10Reference
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtCallableSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.readWriteAccessWithFullExpressionWithPossibleResolve
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.unqualifiedSuper.resolveUnqualifiedSuperFromExpressionContext
import org.jetbrains.kotlin.types.typeUtil.builtIns

abstract class KtFe10SimpleNameReference(expression: KtSimpleNameExpression) : KtSimpleNameReference(expression), KtFe10Reference {
    override fun KtAnalysisSession.resolveToSymbols(): Collection<KtSymbol> {
        require(this is KtFe10AnalysisSession)

        val descriptors = resolveToDescriptors()

        return descriptors.flatMap { descriptor ->
            if (descriptor is SyntheticJavaPropertyDescriptor) {
                @Suppress("DEPRECATION")
                val access = expression.readWriteAccessWithFullExpressionWithPossibleResolve(
                    readWriteAccessWithFullExpressionByResolve = { null }
                ).first

                return@flatMap when (access) {
                    ReferenceAccess.READ -> listOfNotNull(descriptor.getMethod.toKtCallableSymbol(analysisContext))
                    ReferenceAccess.WRITE -> listOfNotNull(descriptor.setMethod?.toKtCallableSymbol(analysisContext))
                    ReferenceAccess.READ_WRITE -> listOfNotNull(
                        descriptor.getMethod.toKtCallableSymbol(analysisContext),
                        descriptor.setMethod?.toKtCallableSymbol(analysisContext)
                    )
                }
            }

            listOfNotNull(descriptor.toKtSymbol(analysisContext))
        }
    }

    private fun KtFe10AnalysisSession.resolveToDescriptors(): List<DeclarationDescriptor> {
        val instanceExpression = getContainingInstanceExpression()
        if (instanceExpression != null) {
            val instanceDescriptor = resolveInstanceExpressionToDescriptor(instanceExpression)
            return if (instanceDescriptor != null) listOf(instanceDescriptor) else emptyList()
        }

        val importDirective = expression.getParentOfType<KtImportDirective>(strict = true)
        if (importDirective != null) {
            val trace = DelegatingBindingTrace(analysisContext.resolveSession.bindingContext, "Trace for import resolution")
            val result = analysisContext.qualifiedExpressionResolver.processImportReference(
                importDirective = importDirective,
                moduleDescriptor = analysisContext.resolveSession.moduleDescriptor,
                trace = trace,
                excludedImportNames = emptyList(),
                packageFragmentForVisibilityCheck = null
            )

            val parent = expression.parent
            return if (parent is KtQualifiedExpression && parent.selectorExpression == expression && parent.parent is KtImportDirective) {
                // For complete import references, return all resulting declarations
                result?.getContributedDescriptors()?.sortedBy { it !is CallableDescriptor } ?: emptyList()
            } else {
                listOfNotNull(trace[BindingContext.REFERENCE_TARGET, expression])
            }
        }

        val bindingContext = analysisContext.analyze(expression, AnalysisMode.PARTIAL)

        val callableReference = getContainingCallableReferenceForClassifier()
        if (callableReference != null) {
            val resolvedCall = callableReference.callableReference.getResolvedCall(bindingContext)
            val callableDescriptor = resolvedCall?.candidateDescriptor
            if (callableDescriptor != null) {
                // Return actual callable parent (that may be a companion object)
                val ownerClassifierDescriptor = callableDescriptor.containingDeclaration.takeUnless { it is PackageFragmentDescriptor }
                    ?: callableDescriptor.extensionReceiverParameter?.value?.type?.constructor?.declarationDescriptor

                if (ownerClassifierDescriptor != null) {
                    return listOf(ownerClassifierDescriptor)
                }
            }
        }

        val targetDescriptor: DeclarationDescriptor? = when (val resolvedCall = expression.getResolvedCall(bindingContext)) {
            is VariableAsFunctionResolvedCall -> resolvedCall.variableCall.candidateDescriptor
            null -> bindingContext[BindingContext.REFERENCE_TARGET, expression]
            else -> resolvedCall.candidateDescriptor
        }

        return if (targetDescriptor != null) listOf(unwrapTargetDescriptor(targetDescriptor)) else emptyList()
    }

    private fun KtFe10AnalysisSession.resolveInstanceExpressionToDescriptor(
        instanceExpression: KtInstanceExpressionWithLabel
    ): DeclarationDescriptor? {
        val bindingContext = analysisContext.analyze(instanceExpression, AnalysisMode.PARTIAL)

        return when (instanceExpression) {
            is KtSuperExpression -> {
                when (val superTypeQualifier = instanceExpression.superTypeQualifier) {
                    null -> {
                        val selfType = bindingContext[BindingContext.THIS_TYPE_FOR_SUPER_EXPRESSION, instanceExpression] ?: return null
                        if (expression is KtLabelReferenceExpression) {
                            // For a label of a qualified super call, return the current class descriptor
                            selfType.constructor.declarationDescriptor
                        } else {
                            val matchedSuperTypes = resolveUnqualifiedSuperFromExpressionContext(
                                instanceExpression,
                                TypeUtils.getImmediateSupertypes(selfType),
                                selfType.builtIns.anyType
                            )
                            matchedSuperTypes.singleOrNull()?.constructor?.declarationDescriptor
                        }
                    }
                    else -> bindingContext[BindingContext.TYPE, superTypeQualifier]?.constructor?.declarationDescriptor
                }
            }
            is KtThisExpression -> {
                val call = bindingContext[BindingContext.CALL, instanceExpression]
                val resolvedCall = bindingContext[BindingContext.RESOLVED_CALL, call]
                val extensionParameterDescriptor = resolvedCall?.candidateDescriptor
                if (expression is KtLabelReferenceExpression) {
                    // For a label of a 'this' call, return the callable owning the extension receiver parameter
                    extensionParameterDescriptor?.containingDeclaration
                } else {
                    extensionParameterDescriptor
                }
            }
            else -> null
        }
    }

    private fun unwrapTargetDescriptor(targetDescriptor: DeclarationDescriptor): DeclarationDescriptor {
        when (targetDescriptor) {
            is ConstructorDescriptor -> {
                val parentCall = expression.parent as? KtCallExpression
                if (parentCall != null && parentCall.valueArgumentList == null) {
                    // Return the constructed class in case of invalid constructor calls
                    return unwrapConstructedClass(targetDescriptor)
                }
            }
            is FakeCallableDescriptorForObject -> {
                return targetDescriptor.getReferencedDescriptor()
            }
        }

        return targetDescriptor
    }

    // Returns the containing instance expression if either on this/super keyword, or on a label.
    private fun getContainingInstanceExpression(): KtInstanceExpressionWithLabel? {
        return when (expression) {
            is KtLabelReferenceExpression -> {
                val containerNode = expression.parent as? KtContainerNode
                return containerNode?.parent as? KtInstanceExpressionWithLabel
            }
            else -> expression.parent as? KtInstanceExpressionWithLabel
        }
    }

    // Return the containing callable if the 'expression' is a direct semantic receiver of it (e.g. foo.!Bar!::x).
    private fun getContainingCallableReferenceForClassifier(): KtCallableReferenceExpression? {
        var current: PsiElement = expression

        while (true) {
            current = when (val parent = current.parent) {
                is KtCallableReferenceExpression -> return if (current == parent.receiverExpression) parent else null
                is KtDotQualifiedExpression -> parent.takeIf { current == parent.selectorExpression } ?: return null
                is KtAnnotatedExpression -> parent.takeIf { current == parent.baseExpression } ?: return null
                is KtLabeledExpression -> parent.takeIf { current == parent.baseExpression } ?: return null
                is KtParenthesizedExpression -> parent
                else -> return null
            }
        }
    }

    private fun unwrapConstructedClass(constructorDescriptor: ConstructorDescriptor): ClassifierDescriptor {
        return when (constructorDescriptor) {
            is TypeAliasConstructorDescriptor -> constructorDescriptor.typeAliasDescriptor
            else -> constructorDescriptor.constructedClass
        }
    }
}

internal class CliKtFe10SimpleNameReference(
    expression: KtSimpleNameExpression
) : KtFe10SimpleNameReference(expression), CliKtFe10Reference {
    override fun doCanBeReferenceTo(candidateTarget: PsiElement): Boolean {
        return true
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        return resolve() == element
    }

    override fun handleElementRename(newElementName: String): PsiElement? {
        throw NotImplementedError("Renaming is not supported in CLI implementation")
    }

    override fun bindToElement(element: PsiElement, shorteningMode: ShorteningMode): PsiElement {
        throw NotImplementedError("Binding is not supported in CLI implementation")
    }

    override fun bindToFqName(fqName: FqName, shorteningMode: ShorteningMode, targetElement: PsiElement?): PsiElement {
        throw NotImplementedError("Binding is not supported in CLI implementation")
    }

    override fun getImportAlias(): KtImportAlias? {
        throw NotImplementedError("Import alias resolution is not supported in CLI implementation")
    }
}