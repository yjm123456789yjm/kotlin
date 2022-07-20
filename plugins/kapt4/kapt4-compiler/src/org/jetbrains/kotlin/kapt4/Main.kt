/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.sun.tools.javac.tree.JCTree
import com.sun.tools.javac.tree.TreeMaker
import com.sun.tools.javac.util.Context
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.kapt3.base.KaptContext
import org.jetbrains.kotlin.kapt3.base.stubs.KaptStubLineInformation
import org.jetbrains.kotlin.kapt3.base.stubs.KotlinPosition
import org.jetbrains.kotlin.kapt3.base.util.KaptLogger
import org.jetbrains.kotlin.kapt3.base.util.WriterBackedKaptLogger
import org.jetbrains.kotlin.light.classes.symbol.caches.SymbolLightClassFacadeCache
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

object Kapt4Main {
    fun run(configuration: CompilerConfiguration, options: KaptOptions): Pair<Kapt4ContextForStubGeneration, Map<KtLightClass, StubGenerator.KaptStub?>> {
        val module: KtSourceModule

        val analysisSession = buildStandaloneAnalysisAPISession {
            // I have files: List<File>
            buildKtModuleProviderByCompilerConfiguration(configuration) {
                module = it
            }
        }
        val project = analysisSession.project
        val lightClassFacadeCache = project.getService(SymbolLightClassFacadeCache::class.java)
        val ktFiles = module.ktFiles

        val lightClasses = buildList {
            ktFiles.flatMapTo(this) { file ->
                file.children.filterIsInstance<KtClassOrObject>().mapNotNull {
                    it.toLightClass()
                }
            }
            ktFiles.mapNotNullTo(this) { it.findFacadeClass() }
        }

        val context = Kapt4ContextForStubGeneration(
            options,
            withJdk = false,
            WriterBackedKaptLogger(isVerbose = false),
            lightClasses,
            emptyMap(),
        )

        val generator = with(context) { StubGenerator() }
        return context to generator.generateStubs()
    }
}

class Kapt4ContextForStubGeneration(
    options: KaptOptions,
    withJdk: Boolean,
    logger: KaptLogger,
    val classes: List<KtLightClass>,
    val origins: Map<Any, JvmDeclarationOrigin>,
) : KaptContext(options, withJdk, logger) {
    val treeMaker = TreeMaker.instance(context) as Kapt4TreeMaker

    override fun preregisterTreeMaker(context: Context) {
        Kapt4TreeMaker.preRegister(context, this)
    }

    override fun close() {
        TODO()
    }
}

class Kapt4LineMappingCollector() {
    private val lineInfo: MutableMap<String, KotlinPosition> = mutableMapOf()
    private val signatureInfo = mutableMapOf<String, String>()

    private val filePaths = mutableMapOf<PsiFile, Pair<String, Boolean>>()

    fun registerClass(lightClass: KtLightClass) {
//        register(lightClass, lightClass.name)
    }

    fun registerMethod(lightClass: KtLightClass, method: PsiMethod) {
//        register(method, lightClass.name + "#" + method.name + method.desc)
    }

    fun registerField(lightClass: KtLightClass, field: PsiField) {
//        register(field, lightClass.name + "#" + field.name)
    }

    fun registerSignature(declaration: JCTree.JCMethodDecl, method: PsiMethod) {
//        signatureInfo[declaration.getJavacSignature()] = method.name + method.desc
    }

    fun getPosition(lightClass: KtLightClass): KotlinPosition? = TODO() // lineInfo[lightClass.name]
    fun getPosition(lightClass: KtLightClass, method: PsiMethod): KotlinPosition? =
        TODO() // lineInfo[lightClass.name + "#" + method.name + method.desc]

    fun getPosition(lightClass: KtLightClass, field: PsiField): KotlinPosition? = TODO() // lineInfo[lightClass.name + "#" + field.name]

//    private fun register(asmNode: Any, fqName: String) {
//        val psiElement = kaptContext.origins[asmNode]?.element ?: return
//        register(fqName, psiElement)
//    }
//
//    private fun register(fqName: String, psiElement: PsiElement) {
//        val containingVirtualFile = psiElement.containingFile.virtualFile
//        if (containingVirtualFile == null || FileDocumentManager.getInstance().getDocument(containingVirtualFile) == null) {
//            return
//        }
//
//        val textRange = psiElement.textRange ?: return
//
//        val (path, isRelative) = getFilePathRelativePreferred(psiElement.containingFile)
//        lineInfo[fqName] = KotlinPosition(path, isRelative, textRange.startOffset)
//    }
//
//    private fun getFilePathRelativePreferred(file: PsiFile): Pair<String, Boolean> {
//        return filePaths.getOrPut(file) {
//            val absolutePath = file.virtualFile.canonicalPath ?: file.virtualFile.path
//            val absoluteFile = File(absolutePath)
//            val baseFile = file.project.basePath?.let { File(it) }
//
//            if (absoluteFile.exists() && baseFile != null && baseFile.exists()) {
//                val relativePath = absoluteFile.relativeToOrNull(baseFile)?.path
//                if (relativePath != null) {
//                    return@getOrPut Pair(relativePath, true)
//                }
//            }
//
//            return@getOrPut Pair(absolutePath, false)
//        }
//    }

    fun serialize(): ByteArray {
        val os = ByteArrayOutputStream()
        val oos = ObjectOutputStream(os)

        oos.writeInt(KaptStubLineInformation.METADATA_VERSION)

        oos.writeInt(lineInfo.size)
        for ((fqName, kotlinPosition) in lineInfo) {
            oos.writeUTF(fqName)
            oos.writeUTF(kotlinPosition.path)
            oos.writeBoolean(kotlinPosition.isRelativePath)
            oos.writeInt(kotlinPosition.pos)
        }

        oos.writeInt(signatureInfo.size)
        for ((javacSignature, methodDesc) in signatureInfo) {
            oos.writeUTF(javacSignature)
            oos.writeUTF(methodDesc)
        }

        oos.flush()
        return os.toByteArray()
    }
}

private const val LONG_DEPRECATED = Opcodes.ACC_DEPRECATED.toLong()
internal fun isDeprecated(access: Long) = (access and LONG_DEPRECATED) != 0L
internal fun isEnum(access: Int) = (access and Opcodes.ACC_ENUM) != 0
internal fun isPublic(access: Int) = (access and Opcodes.ACC_PUBLIC) != 0
internal fun isSynthetic(access: Int) = (access and Opcodes.ACC_SYNTHETIC) != 0
internal fun isFinal(access: Int) = (access and Opcodes.ACC_FINAL) != 0
internal fun isStatic(access: Int) = (access and Opcodes.ACC_STATIC) != 0
internal fun isAbstract(access: Int) = (access and Opcodes.ACC_ABSTRACT) != 0
