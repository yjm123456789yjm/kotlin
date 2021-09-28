/*
 * Copyright 2000-2017 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.utils

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

open class KotlinExceptionWithAttachments : RuntimeException, ExceptionWithAttachments {
    private val attachments = mutableListOf<Attachment>()

    constructor(message: String) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause) {
        if (cause is KotlinExceptionWithAttachments) {
            attachments.addAll(cause.attachments)
        }
    }

    override fun getAttachments(): Array<Attachment> = attachments.toTypedArray()

    fun withAttachment(name: String, content: Any?): KotlinExceptionWithAttachments {
        attachments.add(Attachment(name, content?.toString() ?: "<null>"))
        return this
    }

    fun withPsiAttachment(name: String, element: PsiElement?): KotlinExceptionWithAttachments {
        kotlin.runCatching { element?.getElementTextWithContext() }.getOrNull()?.let { withAttachment(name, it) }
        return this
    }
}

@OptIn(ExperimentalContracts::class)
inline fun checkWithAttachment(value: Boolean, lazyMessage: () -> String, attachments: (KotlinExceptionWithAttachments) -> Unit = {}) {
    contract { returns() implies(value) }

    if (!value) {
        val e = KotlinExceptionWithAttachments(lazyMessage())
        attachments(e)
        throw e
    }
}

private fun PsiElement.getElementTextWithContext(): String {
    if (!isValid) return "<invalid element $this>"

    if (this is PsiFile) {
        return containingFile.text
    }

    // Find parent for element among file children
    val topLevelElement: PsiElement = PsiTreeUtil.findFirstParent(this, { it.parent is PsiFile })
        ?: throw AssertionError("For non-file element we should always be able to find parent in file children")

    val startContextOffset = topLevelElement.textRange.startOffset
    val elementContextOffset = textRange.startOffset

    val inFileParentOffset = elementContextOffset - startContextOffset


    val isInjected = containingFile is VirtualFileWindow
    return StringBuilder(topLevelElement.text)
        .insert(inFileParentOffset, "<caret>")
        .insert(0, "File name: ${containingFile.name} Physical: ${containingFile.isPhysical} Injected: $isInjected\n")
        .toString()
}