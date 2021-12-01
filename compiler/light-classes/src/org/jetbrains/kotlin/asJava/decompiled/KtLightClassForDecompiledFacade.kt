/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.decompiled

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.decompiler.classFile.KtClsFile
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.load.java.structure.LightClassOriginKind
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

class KtLightClassForDecompiledFacade(
    override val clsDelegate: PsiClass,
    clsParent: PsiElement,
    file: KtClsFile,
    kotlinOrigin: KtClassOrObject?,
    override val files: Collection<KtFile>,
) : KtLightClassForDecompiledDeclaration(clsDelegate, clsParent, file, kotlinOrigin), KtLightClassForFacade {
    override fun getName(): String = super<KtLightClassForFacade>.getName()

    override val facadeClassFqName: FqName = file.javaFileFacadeFqName

    override val originKind: LightClassOriginKind
        get() = LightClassOriginKind.BINARY
}
