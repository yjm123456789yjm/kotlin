/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.diagnostics.unwrapParenthesesLabelsAndAnnotations
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.unwrapParenthesesLabelsAndAnnotations

fun FirSourceElement.getChild(type: IElementType, index: Int = 0, depth: Int = -1): FirSourceElement? {
    return getChild(setOf(type), index, depth)
}

fun FirSourceElement.getChild(types: TokenSet, index: Int = 0, depth: Int = -1): FirSourceElement? {
    return getChild(types.types.toSet(), index, depth)
}

fun FirSourceElement.getChild(types: Set<IElementType>, index: Int = 0, depth: Int = -1): FirSourceElement? {
    return when (this) {
        is FirPsiSourceElement -> {
            getChild(types, index, depth)
        }
        is FirLightSourceElement -> {
            getChild(types, index, depth)
        }
        else -> null
    }
}

private fun FirPsiSourceElement.getChild(types: Set<IElementType>, index: Int, depth: Int): FirSourceElement? {
    val visitor = PsiElementFinderByType(types, index, depth)
    return visitor.find(psi)?.toFirPsiSourceElement()
}

private fun FirLightSourceElement.getChild(types: Set<IElementType>, index: Int, depth: Int): FirSourceElement? {
    val visitor = LighterTreeElementFinderByType(treeStructure, types, index, depth)
    val childNode = visitor.find(lighterASTNode) ?: return null
    return buildChildSourceElement(childNode)
}


@Suppress("UNCHECKED_CAST")
fun <T : FirSourceElement> T.getParents(): Sequence<T> =
    when (this) {
        is FirPsiSourceElement -> getParentsPsi() as Sequence<T>
        is FirLightSourceElement -> getParentsLt() as Sequence<T>
        else -> emptySequence()
    }

private fun FirPsiSourceElement.getParentsPsi(): Sequence<FirPsiSourceElement> {
    return psi.parents.map { it.toFirPsiSourceElement() }
}

private fun FirLightSourceElement.getParentsLt(): Sequence<FirLightSourceElement> {
    val offsetDelta = startOffset - lighterASTNode.startOffset
    return generateSequence(this) {
        val parentNode = treeStructure.getParent(it.lighterASTNode) ?: return@generateSequence null
        parentNode.toFirLightSourceElement(
            treeStructure,
            startOffset = parentNode.startOffset + offsetDelta,
            endOffset = parentNode.endOffset + offsetDelta
        )
    }.drop(1)
}

@Suppress("UNCHECKED_CAST")
fun <T : FirSourceElement> T.getPrevSiblings(): Sequence<T> =
    when (this) {
        is FirPsiSourceElement -> getPrevSiblingsPsi() as Sequence<T>
        is FirLightSourceElement -> getPrevSiblingsLt() as Sequence<T>
        else -> emptySequence()
    }

private fun FirPsiSourceElement.getPrevSiblingsPsi(): Sequence<FirPsiSourceElement> {
    return generateSequence(this) { it.psi.prevSibling?.toFirPsiSourceElement() }.drop(1)
}

private fun FirLightSourceElement.getPrevSiblingsLt(): Sequence<FirLightSourceElement> {
    val parent = getParentsLt().firstOrNull() ?: return emptySequence()
    val offsetDelta = startOffset - lighterASTNode.startOffset
    val ref = Ref<Array<LighterASTNode?>>()
    treeStructure.getChildren(parent.lighterASTNode, ref)
    return (ref.get()?.filterNotNull() ?: emptyList())
        .takeWhile { it != lighterASTNode }
        .asReversed()
        .asSequence()
        .map {
            it.toFirLightSourceElement(
                treeStructure,
                startOffset = it.startOffset + offsetDelta,
                endOffset = it.endOffset + offsetDelta
            )
        }
}

@Suppress("UNCHECKED_CAST")
fun <T : FirSourceElement> T.unwrap(): T {
    return when (this) {
        is FirPsiSourceElement -> unwrapPsi() as T
        is FirLightSourceElement -> unwrapLt() as T
        else -> this
    }
}

private fun FirPsiSourceElement.unwrapPsi(): FirPsiSourceElement =
    (psi.unwrapParenthesesLabelsAndAnnotations() ?: psi).toFirPsiSourceElement()

private fun FirLightSourceElement.unwrapLt(): FirLightSourceElement =
    buildChildSourceElement(treeStructure.unwrapParenthesesLabelsAndAnnotations(lighterASTNode))

/**
 * Keeps 'padding' of parent node in child node
 */
internal fun FirLightSourceElement.buildChildSourceElement(childNode: LighterASTNode): FirLightSourceElement {
    val offsetDelta = startOffset - lighterASTNode.startOffset
    return childNode.toFirLightSourceElement(
        treeStructure,
        startOffset = childNode.startOffset + offsetDelta,
        endOffset = childNode.endOffset + offsetDelta
    )
}

