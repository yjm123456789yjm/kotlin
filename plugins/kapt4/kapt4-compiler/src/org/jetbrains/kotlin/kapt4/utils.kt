/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.org.objectweb.asm.Opcodes
import java.util.*

val PsiModifierListOwner.isPublic: Boolean get() = hasModifier(JvmModifier.PUBLIC)
val PsiModifierListOwner.isPrivate: Boolean get() = hasModifier(JvmModifier.PRIVATE)
val PsiModifierListOwner.isProtected: Boolean get() = hasModifier(JvmModifier.PROTECTED)

val PsiModifierListOwner.isFinal: Boolean get() = hasModifier(JvmModifier.FINAL)
val PsiModifierListOwner.isAbstract: Boolean get() = hasModifier(JvmModifier.ABSTRACT)

val PsiModifierListOwner.isStatic: Boolean get() = hasModifier(JvmModifier.STATIC)
val PsiModifierListOwner.isSynthetic: Boolean get() = false //TODO()
val PsiModifierListOwner.isVolatile: Boolean get() = hasModifier(JvmModifier.VOLATILE)
val PsiModifierListOwner.isSynchronized: Boolean get() = hasModifier(JvmModifier.SYNCHRONIZED)
val PsiModifierListOwner.isNative: Boolean get() = hasModifier(JvmModifier.NATIVE)
val PsiModifierListOwner.isStrict: Boolean get() = hasModifier(JvmModifier.STRICTFP)
val PsiModifierListOwner.isTransient: Boolean get() = hasModifier(JvmModifier.TRANSIENT)

typealias JavacList<T> = com.sun.tools.javac.util.List<T>

inline fun <T, R> mapJList(values: Array<T>?, f: (T) -> R?): JavacList<R> {
    return mapJList(values?.asList(), f)
}

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
    get() {
        if (this is PsiPrimitiveType) return name
        return when (val resolvedClass = resolvedClass) {
            is PsiTypeParameter -> resolvedClass.name
            else -> resolvedClass?.qualifiedName
        } ?: NO_NAME_PROVIDED
    }

val PsiElement.ktOrigin: KtElement
    get() = (this as? KtLightElement<*, *>)?.kotlinOrigin ?: TODO()

val PsiClass.defaultType: PsiType
    get() = PsiTypesUtil.getClassType(this)


val PsiType.resolvedClass: PsiClass?
    get() = (this as? PsiClassType)?.resolve()

val PsiModifierListOwner.accessFlags: Int
    get() = when (this) {
        is PsiClass -> computeClassAccessFlags(this)
        is PsiMethod -> computeMethodAccessFlags(this)
        is PsiField -> computeFieldAccessFlags(this)
        is PsiParameter -> computeParameterAccessFlags(this)
        else -> 0
    }

private fun computeCommonAccessFlags(declaration: PsiModifierListOwner): Int {
    /*
     * int ACC_PUBLIC = 0x0001; // class, field, method
     * int ACC_PRIVATE = 0x0002; // class, field, method
     * int ACC_PROTECTED = 0x0004; // class, field, method
     * int ACC_FINAL = 0x0010; // class, field, method, parameter
     * int ACC_SYNTHETIC = 0x1000; // class, field, method, parameter, module *  <-- TODO
     * int ACC_MANDATED = 0x8000; // field, method, parameter, module, module * <-- TODO
     * int ACC_DEPRECATED = 0x20000; // class, field, method
     */
    var access = 0
    val visibilityFlag = when {
        declaration.isPublic -> Opcodes.ACC_PUBLIC
        declaration.isPrivate -> Opcodes.ACC_PRIVATE
        declaration.isProtected -> Opcodes.ACC_PROTECTED
        else -> 0
    }
    access = access or visibilityFlag
    if (declaration.isFinal) {
        access = access or Opcodes.ACC_FINAL
    }
    if (declaration.annotations.any { it.hasQualifiedName(StandardNames.FqNames.deprecated.asString()) }) {
        access = access or Opcodes.ACC_DEPRECATED
    }
    return access
}

private fun computeParameterAccessFlags(parameter: PsiParameter): Int {
    /*
     * int ACC_SYNTHETIC = 0x1000; // class, field, method, parameter, module *
     * int ACC_MANDATED = 0x8000; // field, method, parameter, module, module * <-- TODO
     */
    return if (parameter.isFinal) {
        Opcodes.ACC_FINAL
    } else {
        0
    }
}

// TODO
//val parentClass = lightClass.parent as? PsiClass
//
//var access = lightClass.accessFlags
//access = access or when {
//    lightClass.isRecord -> Opcodes.ACC_RECORD
//    lightClass.isInterface -> Opcodes.ACC_INTERFACE
//    lightClass.isEnum -> Opcodes.ACC_ENUM
//    else -> 0
//}
//
//if (parentClass?.isInterface == true) {
//    // Classes inside interfaces should always be public and static.
//    // See com.sun.tools.javac.comp.Enter.visitClassDef for more information.
//    return (access or Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC) and
//            Opcodes.ACC_PRIVATE.inv() and Opcodes.ACC_PROTECTED.inv() // Remove private and protected modifiers
//}
//
//if (isNested) {
//    access = access or Opcodes.ACC_STATIC
//}
//if (lightClass.isAnnotationType) {
//    access = access or Opcodes.ACC_ANNOTATION
//}
//return access
private fun computeClassAccessFlags(klass: PsiClass): Int {
    /*
     * int ACC_SUPER = 0x0020; // class <-- TODO
     * int ACC_INTERFACE = 0x0200; // class
     * int ACC_ABSTRACT = 0x0400; // class, method
     * int ACC_ANNOTATION = 0x2000; // class
     * int ACC_ENUM = 0x4000; // class(?) field inner
     * int ACC_MODULE = 0x8000; // class <-- TODO
     * int ACC_RECORD = 0x10000; // class
     */
    var access = computeCommonAccessFlags(klass)
    val classKindFlag = when {
        klass.isInterface -> Opcodes.ACC_INTERFACE
        klass.isAnnotationType -> Opcodes.ACC_ANNOTATION
        klass.isEnum -> {
            // enum can not be final
            access = access and Opcodes.ACC_FINAL.inv()
            Opcodes.ACC_ENUM
        }

        else -> 0
    }
    access = access or classKindFlag
    if (klass.isAbstract) {
        access = access or Opcodes.ACC_ABSTRACT
    }
    if (klass.isRecord) {
        access = access or Opcodes.ACC_RECORD
    }
    return access
}

private fun computeMethodAccessFlags(method: PsiMethod): Int {
    /*
     * int ACC_STATIC = 0x0008; // field, method
     * int ACC_SYNCHRONIZED = 0x0020; // method
     * int ACC_BRIDGE = 0x0040; // method <-- TODO
     * int ACC_VARARGS = 0x0080; // method
     * int ACC_NATIVE = 0x0100; // method
     * int ACC_ABSTRACT = 0x0400; // class, method
     * int ACC_STRICT = 0x0800; // method
     * int ACC_MANDATED = 0x8000; // field, method, parameter, module, module * <-- TODO
     */
    var access = computeCommonAccessFlags(method)
    if (method.isStatic) {
        access = access or Opcodes.ACC_STATIC
    }
    if (method.isSynchronized) {
        access = access or Opcodes.ACC_SYNCHRONIZED
    }
    if (method.parameters.any { (it as PsiParameter).isVarArgs }) {
        access = access or Opcodes.ACC_VARARGS
    }
    if (method.isNative) {
        access = access or Opcodes.ACC_NATIVE
    }
    if (method.isAbstract) {
        access = access or Opcodes.ACC_ABSTRACT
    }
    if (method.isStrict) {
        access = access or Opcodes.ACC_STRICT
    }
    return access
}

private fun computeFieldAccessFlags(field: PsiField): Int {
    /*
     * int ACC_STATIC = 0x0008; // field, method
     * int ACC_VOLATILE = 0x0040; // field
     * int ACC_TRANSIENT = 0x0080; // field
     * int ACC_SYNTHETIC = 0x1000; // class, field, method, parameter, module * <-- TODO
     * int ACC_ENUM = 0x4000; // class(?) field inner
     * int ACC_MANDATED = 0x8000; // field, method, parameter, module, module * <-- TODO
     */
    var access = computeCommonAccessFlags(field)
    if (field.isStatic) {
        access = access or Opcodes.ACC_STATIC
    }
    if (field.isVolatile) {
        access = access or Opcodes.ACC_VOLATILE
    }
    if (field.isTransient) {
        access = access or Opcodes.ACC_TRANSIENT
    }
    if (field is PsiEnumConstant) {
        access = access or Opcodes.ACC_ENUM
    }
    return access
}

