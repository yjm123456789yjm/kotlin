/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("unused")

package org.jetbrains.kotlin.asJava.classes

import com.intellij.openapi.components.ServiceManager
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript

interface KotlinLightClassFactory {
    fun createClass(classOrObject: KtClassOrObject): KtLightClassForSourceDeclaration?
    fun createFacade(facadeClassFqName: FqName, files: List<KtFile>): KtLightClassForFacade
    fun createFacadeForSyntheticFile(file: KtFile): KtLightClassForFacade
    fun createScript(script: KtScript): KtLightClassForScript?

    companion object {
        private val instance: KotlinLightClassFactory
            get() = ServiceManager.getService(KotlinLightClassFactory::class.java)

        fun createClass(classOrObject: KtClassOrObject): KtLightClassForSourceDeclaration? {
            return instance.createClass(classOrObject)
        }

        fun createFacade(facadeClassFqName: FqName, files: List<KtFile>): KtLightClassForFacade {
            return instance.createFacade(facadeClassFqName, files)
        }

        fun createFacadeForSyntheticFile(file: KtFile): KtLightClassForFacade {
            return instance.createFacadeForSyntheticFile(file)
        }

        fun createScript(script: KtScript): KtLightClassForScript? {
            return instance.createScript(script)
        }
    }
}