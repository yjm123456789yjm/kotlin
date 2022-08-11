/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.jvm.compiler

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.util.SmartList
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.asJava.classes.*
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.fileClasses.isJvmMultifileClassFile
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.load.java.components.FilesByFacadeFqNameIndexer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.lazy.ResolveSessionUtils
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

class CliKotlinAsJavaSupport(private val traceHolder: CliTraceHolder) : KotlinAsJavaSupport() {
    override fun getFacadeClassesInPackage(packageFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        return findFacadeFilesInPackage(packageFqName, scope)
            .groupBy { it.javaFileFacadeFqName }
            .mapNotNull { (facadeClassFqName, files) ->
                files.ifNotEmpty { KotlinLightClassFactory.createFacade(facadeClassFqName, files) }
            }
    }

    override fun getFacadeNames(packageFqName: FqName, scope: GlobalSearchScope): Collection<String> {
        return findFacadeFilesInPackage(packageFqName, scope).map { it.javaFileFacadeFqName.shortName().asString() }
    }

    private fun findFacadeFilesInPackage(
        packageFqName: FqName,
        scope: GlobalSearchScope
    ) = traceHolder.bindingContext.get(FilesByFacadeFqNameIndexer.FACADE_FILES_BY_PACKAGE_NAME, packageFqName)
        ?.filter { PsiSearchScopeUtil.isInScope(scope, it) }
        .orEmpty()

    override fun getLightFacade(file: KtFile): KtLightClassForFacade? {
        if (file.isCompiled || file.isScript()) return null

        val facadeFqName = file.javaFileFacadeFqName
        val files = if (file.isJvmMultifileClassFile) {
            findFilesForFacade(facadeFqName, GlobalSearchScope.allScope(file.project)).filter(KtFile::isJvmMultifileClassFile)
        } else {
            listOf(file)
        }

        if (files.none(KtFile::hasTopLevelCallables)) return null
        return KotlinLightClassFactory.createFacade(file.javaFileFacadeFqName, listOf(file))
    }

    override fun getFacadeClasses(facadeFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        if (facadeFqName.isRoot) return emptyList()

        val files = findFilesForFacade(facadeFqName, scope).ifEmpty { return emptyList() }
        return listOfNotNull(KotlinLightClassFactory.createFacade(facadeFqName, files))
    }

    override fun findFilesForFacade(facadeFqName: FqName, searchScope: GlobalSearchScope): List<KtFile> {
        if (facadeFqName.isRoot) return emptyList()

        return traceHolder.bindingContext.get(FilesByFacadeFqNameIndexer.FACADE_FILES_BY_FQ_NAME, facadeFqName)?.filter {
            PsiSearchScopeUtil.isInScope(searchScope, it)
        }.orEmpty()
    }

    override fun getScriptClasses(scriptFqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        if (scriptFqName.isRoot) {
            return emptyList()
        }

        return findFilesForPackage(scriptFqName.parent(), scope).mapNotNull { file ->
            file.script?.takeIf { it.fqName == scriptFqName }?.let { getLightClassForScript(it) }
        }
    }

    override fun getKotlinInternalClasses(fqName: FqName, scope: GlobalSearchScope): Collection<PsiClass> {
        return emptyList()
    }

    override fun getFakeLightClass(classOrObject: KtClassOrObject): KtFakeLightClass =
        KtDescriptorBasedFakeLightClass(classOrObject)

    override fun findClassOrObjectDeclarationsInPackage(
        packageFqName: FqName, searchScope: GlobalSearchScope
    ): Collection<KtClassOrObject> {
        val files = findFilesForPackage(packageFqName, searchScope)
        val result = SmartList<KtClassOrObject>()
        for (file in files) {
            for (declaration in file.declarations) {
                if (declaration is KtClassOrObject) {
                    result.add(declaration)
                }
            }
        }
        return result
    }

    override fun packageExists(fqName: FqName, scope: GlobalSearchScope): Boolean {
        return !traceHolder.module.getPackage(fqName).isEmpty()
    }

    override fun getSubPackages(fqn: FqName, scope: GlobalSearchScope): Collection<FqName> {
        val packageView = traceHolder.module.getPackage(fqn)
        return packageView.memberScope.getContributedDescriptors(
            DescriptorKindFilter.PACKAGES,
            MemberScope.ALL_NAME_FILTER
        ).mapNotNull { member -> (member as? PackageViewDescriptor)?.fqName }
    }

    override fun getLightClass(classOrObject: KtClassOrObject): KtLightClass? = KotlinLightClassFactory.createClass(classOrObject)

    override fun getLightClassForScript(script: KtScript): KtLightClassForScript? = KotlinLightClassFactory.createScript(script)

    override fun findClassOrObjectDeclarations(fqName: FqName, searchScope: GlobalSearchScope): Collection<KtClassOrObject> {
        return ResolveSessionUtils.getClassDescriptorsByFqName(traceHolder.module, fqName).mapNotNull {
            val element = DescriptorToSourceUtils.getSourceFromDescriptor(it)
            if (element is KtClassOrObject && PsiSearchScopeUtil.isInScope(searchScope, element)) {
                element
            } else null
        }
    }

    override fun findFilesForPackage(fqName: FqName, searchScope: GlobalSearchScope): Collection<KtFile> {
        return traceHolder.bindingContext.get(BindingContext.PACKAGE_TO_FILES, fqName)?.filter {
            PsiSearchScopeUtil.isInScope(searchScope, it)
        }.orEmpty()
    }

    override fun createFacadeForSyntheticFile(file: KtFile): KtLightClassForFacade = error("Should not be called")
}
