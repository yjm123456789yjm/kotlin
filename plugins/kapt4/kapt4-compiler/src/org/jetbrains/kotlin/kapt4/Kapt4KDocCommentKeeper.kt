/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import com.sun.tools.javac.tree.DocCommentTable
import com.sun.tools.javac.tree.JCTree

context(Kapt4ContextForStubGeneration)
class Kapt4KDocCommentKeeper {
//    private val docCommentTable = KaptDocCommentTable()

    fun getDocTable(file: JCTree.JCCompilationUnit): DocCommentTable {
        TODO()
//        val map = docCommentTable.takeIf { it.map.isNotEmpty() } ?: return docCommentTable
//
//        // Enum values with doc comments are rendered incorrectly in javac pretty print,
//        // so we delete the comments.
//        file.accept(object : TreeScanner() {
//            var removeComments = false
//
//            override fun visitVarDef(def: JCTree.JCVariableDecl) {
//                if (!removeComments && (def.modifiers.flags and Opcodes.ACC_ENUM.toLong()) != 0L) {
//                    map.removeComment(def)
//
//                    removeComments = true
//                    super.visitVarDef(def)
//                    removeComments = false
//                    return
//                }
//
//                super.visitVarDef(def)
//            }
//
//            override fun scan(tree: JCTree?) {
//                if (removeComments && tree != null) {
//                    map.removeComment(tree)
//                }
//
//                super.scan(tree)
//            }
//        })
//
//        return docCommentTable
    }

    fun saveKDocComment(tree: JCTree, node: Any) {
        TODO()
//        val origin = kaptContext.origins[node] ?: return
//        val psiElement = origin.element as? KtDeclaration ?: return
//        val descriptor = origin.descriptor
//        val docComment = psiElement.docComment ?: return
//
//        if (descriptor is ConstructorDescriptor && psiElement is KtClassOrObject) {
//            // We don't want the class comment to be duplicated on <init>()
//            return
//        }
//
//        if (node is MethodNode
//            && psiElement is KtProperty
//            && descriptor is PropertyAccessorDescriptor
//            && kaptContext.bindingContext[BindingContext.BACKING_FIELD_REQUIRED, descriptor.correspondingProperty] == true
//        ) {
//            // Do not place documentation on backing field and property accessors
//            return
//        }
//
//        if (node is FieldNode && psiElement is KtObjectDeclaration && descriptor == null) {
//            // Do not write KDoc on object instance field
//            return
//        }
//
//        docCommentTable.putComment(tree, KDocComment(escapeNestedComments(extractCommentText(docComment))))
    }

//    private fun escapeNestedComments(text: String): String {
//        val result = StringBuilder()
//
//        var index = 0
//        var commentLevel = 0
//
//        while (index < text.length) {
//            val currentChar = text[index]
//            fun nextChar() = text.getOrNull(index + 1)
//
//            if (currentChar == '/' && nextChar() == '*') {
//                commentLevel++
//                index++
//                result.append("/ *")
//            } else if (currentChar == '*' && nextChar() == '/') {
//                commentLevel = maxOf(0, commentLevel - 1)
//                index++
//                result.append("* /")
//            } else {
//                result.append(currentChar)
//            }
//
//            index++
//        }
//
//        return result.toString()
//    }
//
//    private fun extractCommentText(docComment: KDoc): String {
//        return buildString {
//            docComment.accept(object : PsiRecursiveElementVisitor() {
//                override fun visitElement(element: PsiElement) {
//                    if (element is LeafPsiElement) {
//                        if (element.isKDocLeadingAsterisk()) {
//                            val indent = takeLastWhile { it == ' ' || it == '\t' }.length
//                            if (indent > 0) {
//                                delete(length - indent, length)
//                            }
//                        } else if (!element.isKDocStart() && !element.isKDocEnd()) {
//                            append(element.text)
//                        }
//                    }
//
//                    super.visitElement(element)
//                }
//            })
//        }.trimIndent().trim()
//    }
//
//    private fun LeafPsiElement.isKDocStart() = elementType == KDocTokens.START
//    private fun LeafPsiElement.isKDocEnd() = elementType == KDocTokens.END
//    private fun LeafPsiElement.isKDocLeadingAsterisk() = elementType == KDocTokens.LEADING_ASTERISK
}
