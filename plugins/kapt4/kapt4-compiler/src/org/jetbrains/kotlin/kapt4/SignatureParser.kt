/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import com.intellij.psi.*
import com.sun.tools.javac.tree.JCTree.*
import java.util.*

@Suppress("UnstableApiUsage")
class SignatureParser(private val treeMaker: Kapt4TreeMaker) {
    class ClassGenericSignature(
        val typeParameters: JavacList<JCTypeParameter>,
        val superClass: JCExpression,
        val interfaces: JavacList<JCExpression>
    )

    fun parseClassSignature(psiClass: PsiClass): ClassGenericSignature {
        val superClasses = mutableListOf<JCExpression>()
        val interfaces = mutableListOf<JCExpression>()
        for (superType in psiClass.superTypes) {
            val jType = treeMaker.TypeWithArguments(superType)
            if (superType.resolvedClass?.isInterface == false) {
                superClasses += jType
            } else {
                interfaces += jType
            }
        }
        val jcTypeParameters = mapJList(psiClass.typeParameters) { convertTypeParameter(it) }
        val jcSuperClass = superClasses.firstOrNull() ?: createJavaLangObjectType()
        val jcInterfaces = JavacList.from(interfaces)
        return ClassGenericSignature(jcTypeParameters, jcSuperClass, jcInterfaces)
    }

    private fun createJavaLangObjectType(): JCExpression {
        return treeMaker.FqName("java.lang.Object")
    }

    fun convertTypeParameter(typeParameter: PsiTypeParameter): JCTypeParameter {
        val classBounds = mutableListOf<JCExpression>()
        val interfaceBounds = mutableListOf<JCExpression>()

        val bounds = typeParameter.bounds
        for (bound in bounds) {
            val boundType = bound as? PsiType ?: continue
            val jBound = treeMaker.TypeWithArguments(boundType)
            if (boundType.resolvedClass?.isInterface == false) {
                classBounds += jBound
            } else {
                interfaceBounds += jBound
            }
        }
        if (classBounds.isEmpty() && interfaceBounds.isEmpty()) {
            classBounds += createJavaLangObjectType()
        }
        return treeMaker.TypeParameter(treeMaker.name(typeParameter.name!!), JavacList.from(classBounds + interfaceBounds))
    }
}
