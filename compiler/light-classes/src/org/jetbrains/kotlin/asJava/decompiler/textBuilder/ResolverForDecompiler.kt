/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.decompiler.textBuilder

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

interface ResolverForDecompiler {
    fun resolveTopLevelClass(classId: ClassId): ClassDescriptor?

    fun resolveDeclarationsInFacade(facadeFqName: FqName): List<DeclarationDescriptor>
}
