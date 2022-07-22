/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.psi.KtElement
import java.util.*

val PsiModifierListOwner.isPublic: Boolean get() = hasModifier(JvmModifier.PUBLIC)
val PsiModifierListOwner.isPrivate: Boolean get() = hasModifier(JvmModifier.PRIVATE)
val PsiModifierListOwner.isProtected: Boolean get() = hasModifier(JvmModifier.PROTECTED)

val PsiModifierListOwner.isFinal: Boolean get() = hasModifier(JvmModifier.FINAL)
val PsiModifierListOwner.isAbstract: Boolean get() = hasModifier(JvmModifier.ABSTRACT)

val PsiModifierListOwner.isStatic: Boolean get() = hasModifier(JvmModifier.STATIC)
val PsiModifierListOwner.isSynthetic: Boolean get() = false //TODO()
val PsiModifierListOwner.isVolatile: Boolean get() = hasModifier(JvmModifier.VOLATILE)

typealias JavacList<T> = com.sun.tools.javac.util.List<T>

inline fun <T, R> mapJList(values: Iterable<T>?, f: (T) -> R?): JavacList<R> {
    if (values == null) return JavacList.nil()

    var result = JavacList.nil<R>()
    for (item in values) {
        f(item)?.let { result = result.append(it) }
    }
    return result
}

inline fun <T, R> mapJListIndexed(values: Iterable<T>?, f: (Int, T) -> R?): JavacList<R> {
    if (values == null) return JavacList.nil()

    var result = JavacList.nil<R>()
    values.forEachIndexed { index, item ->
        f(index, item)?.let { result = result.append(it) }
    }
    return result
}

inline fun <T> mapPairedValuesJList(valuePairs: List<Any>?, f: (String, Any) -> T?): JavacList<T> {
    if (valuePairs == null || valuePairs.isEmpty()) return JavacList.nil()

    val size = valuePairs.size
    var result = JavacList.nil<T>()
    assert(size % 2 == 0)
    var index = 0
    while (index < size) {
        val key = valuePairs[index] as String
        val value = valuePairs[index + 1]
        f(key, value)?.let { result = result.prepend(it) }
        index += 2
    }
    return result.reverse()
}

fun pairedListToMap(valuePairs: List<Any>?): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()

    mapPairedValuesJList(valuePairs) { key, value ->
        map.put(key, value)
    }

    return map
}

operator fun <T : Any> JavacList<T>.plus(other: JavacList<T>): JavacList<T> {
    return this.appendList(other)
}

fun <T : Any> Iterable<T>.toJList(): JavacList<T> = JavacList.from(this)

val PsiClass.signature: String
    get() = ClassUtil.getClassObjectPresentation(PsiTypesUtil.getClassType(this))

val PsiMethod.signature: String
    get() = ClassUtil.getAsmMethodSignature(this)

val PsiField.signature: String
    get() = getAsmFieldSignature(this)

val PsiMethod.hasVarargs: Boolean
    get() = TODO() //

private fun getAsmFieldSignature(field: PsiField): String {
    return ClassUtil.getBinaryPresentation(Optional.ofNullable<PsiType>(field.type).orElse(PsiType.VOID))
}

val PsiModifierListOwner.isConstructor: Boolean
    get() = (this is PsiMethod) && this.isConstructor

val PsiType.qualifiedName: String
    get() = resolvedClass?.qualifiedName ?: "<no name provided>"

val PsiElement.ktOrigin: KtElement
    get() = (this as? KtLightElement<*, *>)?.kotlinOrigin ?: TODO()

val PsiClass.defaultType: PsiType
    get() = PsiTypesUtil.getClassType(this)


val PsiType.resolvedClass: PsiClass?
    get() = (this as? PsiClassType)?.resolve()
