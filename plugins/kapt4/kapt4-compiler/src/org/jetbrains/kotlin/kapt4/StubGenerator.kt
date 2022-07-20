/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt4

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.sun.tools.javac.code.Flags
import com.sun.tools.javac.parser.Tokens
import com.sun.tools.javac.tree.JCTree
import com.sun.tools.javac.tree.JCTree.*
import kotlinx.kapt.KaptIgnored
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.base.kapt3.KaptFlag
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.kapt3.base.javac.reportKaptError
import org.jetbrains.kotlin.kapt3.base.stubs.KaptStubLineInformation
import org.jetbrains.kotlin.kapt3.base.stubs.KotlinPosition
import org.jetbrains.kotlin.kapt4.ErrorTypeCorrector.TypeKind.*
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.utils.addToStdlib.runUnless
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.AnnotationNode
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import java.io.File
import javax.lang.model.element.ElementKind

context(Kapt4ContextForStubGeneration)
class StubGenerator {
    private companion object {
        private const val VISIBILITY_MODIFIERS = (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).toLong()
        private const val MODALITY_MODIFIERS = (Opcodes.ACC_FINAL or Opcodes.ACC_ABSTRACT).toLong()

        private const val CLASS_MODIFIERS = VISIBILITY_MODIFIERS or MODALITY_MODIFIERS or
                (Opcodes.ACC_DEPRECATED or Opcodes.ACC_INTERFACE or Opcodes.ACC_ANNOTATION or Opcodes.ACC_ENUM or Opcodes.ACC_STATIC).toLong()

        private const val METHOD_MODIFIERS = VISIBILITY_MODIFIERS or MODALITY_MODIFIERS or
                (Opcodes.ACC_DEPRECATED or Opcodes.ACC_SYNCHRONIZED or Opcodes.ACC_NATIVE or Opcodes.ACC_STATIC or Opcodes.ACC_STRICT).toLong()

        private const val FIELD_MODIFIERS = VISIBILITY_MODIFIERS or MODALITY_MODIFIERS or
                (Opcodes.ACC_VOLATILE or Opcodes.ACC_TRANSIENT or Opcodes.ACC_ENUM or Opcodes.ACC_STATIC).toLong()

        private const val PARAMETER_MODIFIERS = FIELD_MODIFIERS or Flags.PARAMETER or Flags.VARARGS or Opcodes.ACC_FINAL.toLong()

        private val BLACKLISTED_ANNOTATIONS = listOf(
            "java.lang.Deprecated", "kotlin.Deprecated", // Deprecated annotations
            "java.lang.Synthetic",
            "synthetic.kotlin.jvm.GeneratedByJvmOverloads" // kapt3-related annotation for marking JvmOverloads-generated methods
        )

        private val KOTLIN_METADATA_ANNOTATION = Metadata::class.java.name

        private val NON_EXISTENT_CLASS_NAME = FqName("error.NonExistentClass")

        private val JAVA_KEYWORD_FILTER_REGEX = "[a-z]+".toRegex()

        @Suppress("UselessCallOnNotNull") // nullable toString(), KT-27724
        private val JAVA_KEYWORDS = Tokens.TokenKind.values()
            .filter { JAVA_KEYWORD_FILTER_REGEX.matches(it.toString().orEmpty()) }
            .mapTo(hashSetOf(), Any::toString)

        private val KOTLIN_PACKAGE = FqName("kotlin")

        private val ARRAY_OF_FUNCTIONS = (ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY.values + ArrayFqNames.ARRAY_OF_FUNCTION).toSet()
    }

    private val correctErrorTypes = options[KaptFlag.CORRECT_ERROR_TYPES]
    private val strictMode = options[KaptFlag.STRICT]
    private val stripMetadata = options[KaptFlag.STRIP_METADATA]
    private val keepKdocComments = options[KaptFlag.KEEP_KDOC_COMMENTS_IN_STUBS]

    private val signatureParser = SignatureParser(treeMaker)

    // TODO: rename
    private val compiledClassByName = classes.associateBy { it.name!! }

    private val kdocCommentKeeper = if (keepKdocComments) Kapt4KDocCommentKeeper() else null

    fun generateStubs(): Map<KtLightClass, KaptStub?> {
        return classes.associateWith { convertTopLevelClass(it) }
    }

    private fun convertTopLevelClass(lightClass: KtLightClass): KaptStub? {
        val origin = origins[lightClass]// ?: return null // TODO: handle synthetic declarations from plugins
        val ktFile = origin?.element?.containingFile as? KtFile ?: return null
        val lineMappings = Kapt4LineMappingCollector()
        val packageName = (lightClass.parent as? PsiJavaFile)?.packageName ?: TODO()
        val packageClause = runUnless(packageName.isBlank()) { treeMaker.FqName(packageName) }

        val classDeclaration = convertClass(lightClass, lineMappings, packageName, true) ?: return null

        classDeclaration.mods.annotations = classDeclaration.mods.annotations

        val imports = if (correctErrorTypes) convertImports(ktFile, classDeclaration) else JavacList.nil()

        val nonEmptyImports: JavacList<JCTree> = when {
            imports.size > 0 -> imports
            else -> JavacList.of(treeMaker.Import(treeMaker.FqName("java.lang.System"), false))
        }

        val classes = JavacList.of<JCTree>(classDeclaration)

        val topLevel = treeMaker.TopLevelJava9Aware(packageClause, nonEmptyImports + classes)
        if (kdocCommentKeeper != null) {
            topLevel.docComments = kdocCommentKeeper.getDocTable(topLevel)
        }
//        TODO
//        KaptJavaFileObject(topLevel, classDeclaration).apply {
//            topLevel.sourcefile = this
//            mutableBindings[clazz.name] = this
//        }
//
//        postProcess(topLevel)

        return KaptStub(topLevel, lineMappings.serialize())
    }

    private fun convertClass(
        lightClass: KtLightClass,
        lineMappings: Kapt4LineMappingCollector,
        packageFqName: String,
        isTopLevel: Boolean
    ): JCClassDecl? {
//        if (isSynthetic(lightClass.access)) return null
//        if (!checkIfValidTypeName(lightClass, Type.getObjectType(lightClass.name))) return null

        val isInnerOrNested = lightClass.parent is PsiClass
        val isNested = isInnerOrNested && lightClass.isStatic
        val isInner = isInnerOrNested && !isNested


        val flags = getClassAccessFlags(lightClass, isInner)

        val isEnum = lightClass.isEnum()
        val isAnnotation = lightClass.isAnnotationType

        val modifiers = convertModifiers(
            lightClass,
            flags.toLong(),
            if (isEnum) ElementKind.ENUM else ElementKind.CLASS,
            packageFqName,
            lightClass.annotations.toList()
        )

        // TODO: check
        val isDefaultImpls = lightClass.name!!.endsWith("\$DefaultImpls")
                && lightClass.isPublic && lightClass.isFinal && lightClass.isInterface

        // DefaultImpls without any contents don't have INNERCLASS'es inside it (and inside the parent interface)
        if (isDefaultImpls && (isTopLevel || (lightClass.fields.isEmpty() && lightClass.methods.isEmpty()))) {
            return null
        }

        val simpleName = getClassName(lightClass, isDefaultImpls, packageFqName)
        if (!isValidIdentifier(simpleName)) return null

        val interfaces = mapJList(lightClass.interfaces.toList()) {
            if (isAnnotation && it.qualifiedName == "java/lang/annotation/Annotation") return@mapJList null
            treeMaker.FqName(treeMaker.getQualifiedName(it))
        }

        val superClassName = when (val superClass = lightClass.superClass) {
            null -> treeMaker.getQualifiedName(java.lang.Object::class.java.canonicalName)
            else -> treeMaker.getQualifiedName(superClass)
        }

        val superClass = treeMaker.FqName(superClassName)

        val genericType = signatureParser.parseClassSignature(lightClass.signature, superClass, interfaces)
// TODO
//        class EnumValueData(val field: FieldNode, val innerClass: InnerClassNode?, val correspondingClass: ClassNode?)
//
//        val enumValuesData = lightClass.fields.filter { it.isEnumValue() }.map { field ->
//            var foundInnerClass: InnerClassNode? = null
//            var correspondingClass: ClassNode? = null
//
//            for (innerClass in lightClass.innerClasses) {
//                // Class should have the same name as enum value
//                if (innerClass.innerName != field.name) continue
//                val classNode = compiledClassByName[innerClass.name] ?: continue
//
//                // Super class name of the class should be our enum class
//                if (classNode.superName != lightClass.name) continue
//
//                correspondingClass = classNode
//                foundInnerClass = innerClass
//                break
//            }
//
//            EnumValueData(field, foundInnerClass, correspondingClass)
//        }
//
//        val enumValues: JavacList<JCTree> = mapJList(enumValuesData) { data ->
//            val constructorArguments = Type.getArgumentTypes(lightClass.methods.firstOrNull {
//                it.name == "<init>" && Type.getArgumentsAndReturnSizes(it.desc).shr(2) >= 2
//            }?.desc ?: "()Z")
//
//            val args = mapJList(constructorArguments.drop(2)) { convertLiteralExpression(lightClass, getDefaultValue(it)) }
//
//            val def = data.correspondingClass?.let { convertClass(it, lineMappings, packageFqName, false) }
//
//            convertField(
//                data.field, lightClass, lineMappings, packageFqName, treeMaker.NewClass(
//                    /* enclosing = */ null,
//                    /* typeArgs = */ JavacList.nil(),
//                    /* lightClass = */ treeMaker.Ident(treeMaker.name(data.field.name)),
//                    /* args = */ args,
//                    /* def = */ def
//                )
//            )
//        }
//
        val fieldsPositions = mutableMapOf<JCTree, MemberData>()
        val fields = mapJList<PsiField, JCTree>(lightClass.fields.asIterable()) { field ->
//            if (field.isEnumValue()) {
//                null
//            } else {
                convertField(field, lightClass, lineMappings, packageFqName)?.also {
                    fieldsPositions[it] = MemberData(field.name, field.signature, lineMappings.getPosition(lightClass, field))
                }
//            }
        }

        val methodsPositions = mutableMapOf<JCTree, MemberData>()
        val methods = mapJList<PsiMethod, JCTree>(lightClass.methods.asIterable()) { method ->
//            if (isEnum) {
//                if (method.name == "values" && method.desc == "()[L${lightClass.name};") return@mapJList null
//                if (method.name == "valueOf" && method.desc == "(Ljava/lang/String;)L${lightClass.name};") return@mapJList null
//            }

            convertMethod(method, lightClass, lineMappings, packageFqName, isInner)?.also {
                methodsPositions[it] = MemberData(method.name, method.signature, lineMappings.getPosition(lightClass, method))
            }
        }

        val nestedClasses = mapJList(lightClass.getChildrenOfType<PsiClass>().asList()) { innerClass ->
//            TODO
//            if (enumValuesData.any { it.innerClass == innerClass }) return@mapJList null
//            if (innerClass.outerName != lightClass.name) return@mapJList null
            val innerClassNode = compiledClassByName[innerClass.name] ?: return@mapJList null
            convertClass(innerClassNode, lineMappings, packageFqName, false)
        }

        lineMappings.registerClass(lightClass)

        val superTypes = calculateSuperTypes(lightClass, genericType)

        val classPosition = lineMappings.getPosition(lightClass)
        val sortedFields = JavacList.from(fields.sortedWith(MembersPositionComparator(classPosition, fieldsPositions)))
        val sortedMethods = JavacList.from(methods.sortedWith(MembersPositionComparator(classPosition, methodsPositions)))

        return treeMaker.ClassDef(
            modifiers,
            treeMaker.name(simpleName),
            genericType.typeParameters,
            superTypes.superClass,
            superTypes.interfaces,
            /*enumValues + */JavacList.from(sortedFields + sortedMethods + nestedClasses)
        ).keepKdocCommentsIfNecessary(lightClass)
    }

    private class MemberData(val name: String, val descriptor: String, val position: KotlinPosition?)

    private fun convertImports(file: KtFile, classDeclaration: JCClassDecl): JavacList<JCTree> {
        val imports = mutableListOf<JCImport>()
        val importedShortNames = mutableSetOf<String>()

        // We prefer ordinary imports over aliased ones.
        val sortedImportDirectives = file.importDirectives.partition { it.aliasName == null }.run { first + second }

        loop@ for (importDirective in sortedImportDirectives) {
            // Qualified name should be valid Java fq-name
            val importedFqName = importDirective.importedFqName?.takeIf { it.pathSegments().size > 1 } ?: continue
            if (!isValidQualifiedName(importedFqName)) continue

            val shortName = importedFqName.shortName()
            if (shortName.asString() == classDeclaration.simpleName.toString()) continue
//            TODO
//            val importedReference = /* resolveImportReference */ run {
//                val referenceExpression = getReferenceExpression(importDirective.importedReference) ?: return@run null
//
//                val bindingContext = kaptContext.bindingContext
//                bindingContext[BindingContext.REFERENCE_TARGET, referenceExpression]?.let { return@run it }
//
//                val allTargets = bindingContext[BindingContext.AMBIGUOUS_REFERENCE_TARGET, referenceExpression] ?: return@run null
//                allTargets.find { it is CallableDescriptor }?.let { return@run it }
//
//                return@run allTargets.firstOrNull()
//            }

//            val isCallableImport = importedReference is CallableDescriptor
//            val isEnumEntry = (importedReference as? ClassDescriptor)?.kind == ClassKind.ENUM_ENTRY
//            val isAllUnderClassifierImport = importDirective.isAllUnder && importedReference is ClassifierDescriptor
//
//            if (isCallableImport || isEnumEntry || isAllUnderClassifierImport) {
//                continue@loop
//            }
//
//            val importedExpr = treeMaker.FqName(importedFqName.asString())
//
//            imports += if (importDirective.isAllUnder) {
//                treeMaker.Import(treeMaker.Select(importedExpr, treeMaker.nameTable.names.asterisk), false)
//            } else {
//                if (!importedShortNames.add(importedFqName.shortName().asString())) {
//                    continue
//                }
//
//                treeMaker.Import(importedExpr, false)
//            }
        }

        return JavacList.from(imports)
    }

    // Done
    private fun getClassAccessFlags(lightClass: KtLightClass, isNested: Boolean): Int {
        val parentClass = lightClass.parent as? KtLightClass

        var access = getDeclarationAccessFlags(lightClass)
        if (lightClass.isRecord) {
            access = access or Opcodes.ACC_RECORD
        }

        if (parentClass?.isInterface == true) {
            // Classes inside interfaces should always be public and static.
            // See com.sun.tools.javac.comp.Enter.visitClassDef for more information.
            return (access or Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC) and
                    Opcodes.ACC_PRIVATE.inv() and Opcodes.ACC_PROTECTED.inv() // Remove private and protected modifiers
        }

        if (isNested) {
            access = access or Opcodes.ACC_STATIC
        }
        return access
    }

    private fun getDeclarationAccessFlags(callable: PsiModifierListOwner): Int {
        var access = 0
        if (callable.annotations.any { it.hasQualifiedName(StandardNames.FqNames.deprecated.asString()) }) {
            access = access or Opcodes.ACC_DEPRECATED
        }
        val visibilityFlag = when {
            callable.isPublic -> Opcodes.ACC_PUBLIC
            callable.isPrivate -> Opcodes.ACC_PRIVATE
            callable.isProtected -> Opcodes.ACC_PROTECTED
            else -> 0
        }
        access = access or visibilityFlag

        val modalityFlag = when {
            callable.isFinal -> Opcodes.ACC_FINAL
            callable.isAbstract -> Opcodes.ACC_ABSTRACT
            else -> 0
        }

        access = access or modalityFlag

        if (callable.isStatic) {
            access = access or Opcodes.ACC_STATIC
        }

        if (callable.isVolatile) {
            access = access or Opcodes.ACC_VOLATILE
        }

        return access
    }

    // TODO
    private fun convertAnnotation(
        containingClass: KtLightClass,
        annotation: PsiAnnotation,
        packageFqName: String? = "",
        filtered: Boolean = true
    ): JCTree.JCAnnotation? {
        return null
//        val annotationType = Type.getType(annotation.desc)
//        val fqName = treeMaker.getQualifiedName(annotationType)
//
//        if (filtered) {
//            if (BLACKLISTED_ANNOTATIONS.any { fqName.startsWith(it) }) return null
//            if (stripMetadata && fqName == KOTLIN_METADATA_ANNOTATION) return null
//        }
//
//        val ktAnnotation = annotationDescriptor?.source?.getPsi() as? KtAnnotationEntry
//        val annotationFqName = getNonErrorType(
//            annotationDescriptor?.type,
//            ANNOTATION,
//            { ktAnnotation?.typeReference },
//            {
//                val useSimpleName = '.' in fqName && fqName.substringBeforeLast('.', "") == packageFqName
//
//                when {
//                    useSimpleName -> treeMaker.FqName(fqName.substring(packageFqName!!.length + 1))
//                    else -> treeMaker.Type(annotationType)
//                }
//            }
//        )
//
//        val argMapping = ktAnnotation?.calleeExpression
//            ?.getResolvedCall(kaptContext.bindingContext)?.valueArguments
//            ?.mapKeys { it.key.name.asString() }
//            ?: emptyMap()
//
//        val constantValues = pairedListToMap(annotation.values)
//
//        val values = if (argMapping.isNotEmpty()) {
//            argMapping.mapNotNull { (parameterName, arg) ->
//                if (arg is DefaultValueArgument) return@mapNotNull null
//                convertAnnotationArgumentWithName(containingClass, constantValues[parameterName], arg, parameterName)
//            }
//        } else {
//            constantValues.mapNotNull { (parameterName, arg) ->
//                convertAnnotationArgumentWithName(containingClass, arg, null, parameterName)
//            }
//        }
//
//        return treeMaker.Annotation(annotationFqName, com.sun.tools.javac.util.List.from(values))
    }

    private fun convertModifiers(
        containingClass: KtLightClass,
        access: Int,
        kind: ElementKind,
        packageFqName: String,
        allAnnotations: List<PsiAnnotation>
    ): JCTree.JCModifiers {
        return convertModifiers(containingClass, access.toLong(), kind, packageFqName, allAnnotations)
    }
    // TODO
    private fun convertModifiers(
        containingClass: KtLightClass,
        access: Long,
        kind: ElementKind,
        packageFqName: String,
        allAnnotations: List<PsiAnnotation>
    ): JCTree.JCModifiers {
        val visibleAnnotations = mutableListOf<PsiAnnotation>()
        val invisibleAnnotations = mutableListOf<PsiAnnotation>()

        for (annotation in allAnnotations) {
            val annotationClass = annotation.resolveAnnotationType() ?: continue
            // TODO
        }



        var seenOverride = false
        val seenAnnotations = mutableSetOf<PsiAnnotation>()
        fun convertAndAdd(list: JavacList<JCTree.JCAnnotation>, annotation: PsiAnnotation): JavacList<JCTree.JCAnnotation> {
//            if (annotation.desc == "Ljava/lang/Override;") {
//                if (seenOverride) return list  // KT-34569: skip duplicate @Override annotations
//                seenOverride = true
//            }
            // Missing annotation classes can match against multiple annotation descriptors
//            val annotationDescriptor = descriptorAnnotations.firstOrNull {
//                it !in seenAnnotations && checkIfAnnotationValueMatches(annotation, AnnotationValue(it))
//            }?.also {
//                seenAnnotations += it
//            }
            val annotationTree = convertAnnotation(containingClass, annotation, packageFqName) ?: return list
            return list.prepend(annotationTree)
        }

        var annotations = visibleAnnotations.fold(JavacList.nil(), ::convertAndAdd)
        annotations = invisibleAnnotations.fold(annotations, ::convertAndAdd)

        if (isDeprecated(access)) {
            val type = treeMaker.Type(Type.getType(java.lang.Deprecated::class.java))
            annotations = annotations.append(treeMaker.Annotation(type, JavacList.nil()))
        }

        val flags = when (kind) {
            ElementKind.ENUM -> access and CLASS_MODIFIERS and Opcodes.ACC_ABSTRACT.inv().toLong()
            ElementKind.CLASS -> access and CLASS_MODIFIERS
            ElementKind.METHOD -> access and METHOD_MODIFIERS
            ElementKind.FIELD -> access and FIELD_MODIFIERS
            ElementKind.PARAMETER -> access and PARAMETER_MODIFIERS
            else -> throw IllegalArgumentException("Invalid element kind: $kind")
        }
        return treeMaker.Modifiers(flags, annotations)
    }

    class KaptStub(val file: JCTree.JCCompilationUnit, private val kaptMetadata: ByteArray? = null) {
        fun writeMetadataIfNeeded(forSource: File) {
            if (kaptMetadata == null) {
                return
            }

            val metadataFile = File(
                forSource.parentFile,
                forSource.nameWithoutExtension + KaptStubLineInformation.KAPT_METADATA_EXTENSION
            )

            metadataFile.writeBytes(kaptMetadata)
        }
    }

    // TODO
    private fun convertField(
        field: PsiField,
        containingClass: KtLightClass,
        lineMappings: Kapt4LineMappingCollector,
        packageFqName: String,
        explicitInitializer: JCExpression? = null
    ): JCTree.JCVariableDecl? {
//        if (field.isSynthetic || isIgnored(field.invisibleAnnotations)) return null // TODO
        // not needed anymore
        val origin = origins[field]
        val descriptor = origin?.descriptor

        val fieldAnnotations = emptyList<PsiAnnotation>()
//        val fieldAnnotations = when {
//            !isIrBackend && descriptor is PropertyDescriptor -> descriptor.backingField?.annotations
//            else -> descriptor?.annotations
//        } ?: Annotations.EMPTY

        val access = getDeclarationAccessFlags(field)
        val modifiers = convertModifiers(
            containingClass,
            access, ElementKind.FIELD, packageFqName,
            fieldAnnotations
        )

        val name = field.name
        if (!isValidIdentifier(name)) return null

        val type = field.type

        // TODO
//        if (!checkIfValidTypeName(containingClass, type)) {
//            return null
//        }

        fun typeFromAsm() = signatureParser.parseFieldSignature(field.signature, treeMaker.Type(type))

        // Enum type must be an identifier (Javac requirement)
        val typeExpression = if (isEnum(access)) {
            treeMaker.SimpleName(treeMaker.getQualifiedName(type).substringAfterLast('.'))
        } else if (/*descriptor is PropertyDescriptor && descriptor.isDelegated TODO*/false) {
            TODO()
//            getNonErrorType(
//                (origin.element as? KtProperty)?.delegateExpression?.getType(kaptContext.bindingContext),
//                RETURN_TYPE,
//                ktTypeProvider = { null },
//                ifNonError = ::typeFromAsm
//            )
        } else {
            getNonErrorType(
                field.type,
                RETURN_TYPE,
                ktTypeProvider = {
                     TODO()
//                    val fieldOrigin = (origins[field]?.element as? KtCallableDeclaration)
//                        ?.takeIf { it !is KtFunction }
//
//                    fieldOrigin?.typeReference
                },
                ifNonError = ::typeFromAsm
            )
        }

        lineMappings.registerField(containingClass, field)

        val initializer = explicitInitializer ?: TODO() //convertPropertyInitializer(containingClass, field)
        return treeMaker.VarDef(modifiers, treeMaker.name(name), typeExpression, initializer)// TODO: .keepKdocCommentsIfNecessary(field)
    }

    private fun convertMethod(
        method: PsiMethod,
        containingClass: KtLightClass,
        lineMappings: Kapt4LineMappingCollector,
        packageFqName: String,
        isInner: Boolean
    ): JCMethodDecl? {
        if (isIgnored(method.annotations.asList())) return null

        val isAnnotationHolderForProperty =
            method.isSynthetic && method.isStatic && method.name.endsWith(JvmAbi.ANNOTATED_PROPERTY_METHOD_NAME_SUFFIX)

        if (method.isSynthetic && !isAnnotationHolderForProperty) return null

        val isOverridden = false //TODO: descriptor.overriddenDescriptors.isNotEmpty()
        val visibleAnnotations = if (isOverridden) {
            (method.annotations.toList()) // TODO + AnnotationNode(Type.getType(Override::class.java).descriptor)
        } else {
            method.annotations.toList()
        }

        val isConstructor = method.name == "<init>"

        val name = method.name
        if (!isValidIdentifier(name, canBeConstructor = isConstructor)) return null

        val modifiers = convertModifiers(
            containingClass,
            if (containingClass.isEnum && isConstructor)
                (getDeclarationAccessFlags(method).toLong() and VISIBILITY_MODIFIERS.inv())
            else
                getDeclarationAccessFlags(method).toLong(),
            ElementKind.METHOD, packageFqName, visibleAnnotations
        )

        if (containingClass.isInterface && !method.isAbstract && !method.isStatic) {
            modifiers.flags = modifiers.flags or Flags.DEFAULT
        }

        val asmReturnType = method.returnType ?: TODO()
        val jcReturnType = if (isConstructor) null else treeMaker.Type(method.returnType)

        val parametersInfo = method.getParametersInfo(containingClass, isInner)

        if (!checkIfValidTypeName(containingClass, asmReturnType)
            || parametersInfo.any { !checkIfValidTypeName(containingClass, it.type) }
        ) {
            return null
        }

        @Suppress("NAME_SHADOWING")
        val parameters = mapJListIndexed(parametersInfo) { index, info ->
            val lastParameter = index == parametersInfo.lastIndex
            val isArrayType = info.type is PsiArrayType

            val varargs = if (lastParameter && isArrayType && method.hasVarargs) Flags.VARARGS else 0L
            val modifiers = convertModifiers(
                containingClass,
                info.flags or varargs or Flags.PARAMETER,
                ElementKind.PARAMETER,
                packageFqName,
                info.visibleAnnotations + info.invisibleAnnotations // TODO
            )

            val name = info.name.takeIf { isValidIdentifier(it) } ?: ("p" + index + "_" + info.name.hashCode().ushr(1))
            val type = treeMaker.Type(info.type)
            treeMaker.VarDef(modifiers, treeMaker.name(name), type, null)
        }

        val exceptionTypes = mapJList(method.throwsList.referencedTypes.asList()) { treeMaker.FqName(it.qualifiedName) }

        val (genericSignature, returnType) =
            extractMethodSignatureTypes(exceptionTypes, jcReturnType, method, parameters)

        val defaultValue = null //method.annotationDefault?.let { convertLiteralExpression(containingClass, it) }

        val body = if (defaultValue != null) {
            null
        } else if (method.isAbstract) {
            null
        } else if (isConstructor && containingClass.isEnum()) {
            treeMaker.Block(0, JavacList.nil())
        } else if (isConstructor) {
            // We already checked it in convertClass()
            TODO()
//            val declaration = origins[containingClass]?.descriptor as ClassDescriptor
//            val superClass = declaration.getSuperClassOrAny()
//            val superClassConstructor = superClass.constructors.firstOrNull {
//                it.visibility.isVisible(null, it, declaration, useSpecialRulesForPrivateSealedConstructors = true)
//            }
//
//            val superClassConstructorCall = if (superClassConstructor != null) {
//                val args = mapJList(superClassConstructor.valueParameters) { param ->
//                    convertLiteralExpression(containingClass, getDefaultValue(typeMapper.mapType(param.type)))
//                }
//                val call = treeMaker.Apply(JavacList.nil(), treeMaker.SimpleName("super"), args)
//                JavacList.of<JCStatement>(treeMaker.Exec(call))
//            } else {
//                JavacList.nil<JCStatement>()
//            }
//
//            treeMaker.Block(0, superClassConstructorCall)
//            TODO
//        } else if (asmReturnType == Type.VOID_TYPE) {
//            treeMaker.Block(0, JavacList.nil())
        } else {
            TODO()
//            val returnStatement = treeMaker.Return(convertLiteralExpression(containingClass, getDefaultValue(asmReturnType)))
//            treeMaker.Block(0, JavacList.of(returnStatement))
        }

        lineMappings.registerMethod(containingClass, method)

        return treeMaker.MethodDef(
            modifiers, treeMaker.name(name), returnType, genericSignature.typeParameters,
            genericSignature.parameterTypes, genericSignature.exceptionTypes,
            body, defaultValue
        ).keepSignature(lineMappings, method).keepKdocCommentsIfNecessary(method)
    }

    // TODO
    private fun extractMethodSignatureTypes(
        exceptionTypes: JavacList<JCExpression>,
        jcReturnType: JCExpression?,
        method: PsiMethod,
        parameters: JavacList<JCTree.JCVariableDecl>
    ): Pair<SignatureParser.MethodGenericSignature, JCExpression?> {
        val psiElement = origins[method]?.element
        val genericSignature = signatureParser.parseMethodSignature(
            method.signature, parameters, exceptionTypes, jcReturnType,
            nonErrorParameterTypeProvider = { index, lazyType ->
                TODO()
//                if (descriptor is PropertySetterDescriptor && valueParametersFromDescriptor.size == 1 && index == 0) {
//                    getNonErrorType(descriptor.correspondingProperty.returnType, METHOD_PARAMETER_TYPE,
//                                    ktTypeProvider = {
//                                        val setterOrigin = (psiElement as? KtCallableDeclaration)
//                                            ?.takeIf { it !is KtFunction }
//
//                                        setterOrigin?.typeReference
//                                    },
//                                    ifNonError = { lazyType() })
//                } else if (descriptor is FunctionDescriptor && valueParametersFromDescriptor.size == parameters.size) {
//                    val parameterDescriptor = valueParametersFromDescriptor[index]
//                    val sourceElement = when {
//                        psiElement is KtFunction -> psiElement
//                        descriptor is ConstructorDescriptor && descriptor.isPrimary -> (psiElement as? KtClassOrObject)?.primaryConstructor
//                        else -> null
//                    }
//
//                    getNonErrorType(
//                        parameterDescriptor.type, METHOD_PARAMETER_TYPE,
//                        ktTypeProvider = {
//                            if (sourceElement == null) return@getNonErrorType null
//
//                            if (sourceElement.hasDeclaredReturnType() && isContinuationParameter(parameterDescriptor)) {
//                                val continuationTypeFqName = StandardNames.CONTINUATION_INTERFACE_FQ_NAME
//                                val functionReturnType = sourceElement.typeReference!!.text
//                                KtPsiFactory(kaptContext.project).createType("$continuationTypeFqName<$functionReturnType>")
//                            } else {
//                                sourceElement.valueParameters.getOrNull(index)?.typeReference
//                            }
//                        },
//                        ifNonError = { lazyType() })
//                } else {
//                    lazyType()
//                }
            })

        val returnType = getNonErrorType(
            method.returnType, RETURN_TYPE,
            ktTypeProvider = {
                     TODO()
//                when (psiElement) {
//                    is KtFunction -> psiElement.typeReference
//                    is KtProperty -> if (descriptor is PropertyGetterDescriptor) psiElement.typeReference else null
//                    is KtPropertyAccessor -> if (descriptor is PropertyGetterDescriptor) psiElement.property.typeReference else null
//                    is KtParameter -> if (descriptor is PropertyGetterDescriptor) psiElement.typeReference else null
//                    else -> null
//                }
            },
            ifNonError = { genericSignature.returnType }
        )

        return Pair(genericSignature, returnType)
    }

    private fun JCMethodDecl.keepSignature(lineMappings: Kapt4LineMappingCollector, method: PsiMethod): JCMethodDecl {
        lineMappings.registerSignature(this, method)
        return this
    }

    private fun <T : JCTree> T.keepKdocCommentsIfNecessary(node: Any): T {
        kdocCommentKeeper?.saveKDocComment(this, node)
        return this
    }

    private fun isIgnored(annotations: List<PsiAnnotation>?): Boolean {
        val kaptIgnoredAnnotationFqName = KaptIgnored::class.java.canonicalName
        return annotations?.any { it.hasQualifiedName(kaptIgnoredAnnotationFqName) } ?: false
    }

    private tailrec fun checkIfValidTypeName(containingClass: KtLightClass, type: PsiType): Boolean {
        if (type is PsiArrayType) {
            return checkIfValidTypeName(containingClass, type.componentType)
        }

//        TODO
//        if (type.sort != Type.OBJECT) return true

        val internalName = type.qualifiedName
        // Ignore type names with Java keywords in it
        if (internalName.split('/', '.').any { it in JAVA_KEYWORDS }) {
            if (strictMode) {
                reportKaptError(
                    "Can't generate a stub for '${containingClass.qualifiedName}'.",
                    "Type name '${type.qualifiedName}' contains a Java keyword."
                )
            }

            return false
        }

        val clazz = compiledClassByName[internalName] ?: return true

        if (doesInnerClassNameConflictWithOuter(clazz)) {
            if (strictMode) {
                reportKaptError(
                    "Can't generate a stub for '${containingClass.name}'.",
                    "Its name '${clazz.name}' is the same as one of the outer class names.",
                    "Java forbids it. Please change one of the class names."
                )
            }

            return false
        }

        reportIfIllegalTypeUsage(containingClass, type)

        return true
    }

    private fun findContainingClassNode(clazz: PsiClass): PsiClass? {
        val innerClassForOuter = clazz.innerClasses.firstOrNull { it.name == clazz.name } ?: return null
        // return compiledClassByName[innerClassForOuter.outerName]
        TODO()
    }

    // Java forbids outer and inner class names to be the same. Check if the names are different
    private tailrec fun doesInnerClassNameConflictWithOuter(
        clazz: PsiClass,
        outerClass: PsiClass? = findContainingClassNode(clazz)
    ): Boolean {
        if (outerClass == null) return false
        if (treeMaker.getSimpleName(clazz) == treeMaker.getSimpleName(outerClass)) return true
        // Try to find the containing class for outerClassNode (to check the whole tree recursively)
        val containingClassForOuterClass = findContainingClassNode(outerClass) ?: return false
        return doesInnerClassNameConflictWithOuter(clazz, containingClassForOuterClass)
    }

    private fun reportIfIllegalTypeUsage(containingClass: PsiClass, type: PsiType) {
        TODO()
    //        val file = getFileForClass(containingClass)
//        importsFromRoot[file]?.let { importsFromRoot ->
//            val typeName = type.className
//            if (importsFromRoot.contains(typeName)) {
//                val msg = "${containingClass.className}: Can't reference type '${typeName}' from default package in Java stub."
//                if (strictMode) kaptContext.reportKaptError(msg)
//                else kaptContext.logger.warn(msg)
//            }
//        }
    }

    private fun <T : JCExpression?> getNonErrorType(
        type: PsiType?,
        kind: ErrorTypeCorrector.TypeKind,
        ktTypeProvider: () -> KtTypeReference?,
        ifNonError: () -> T
    ): T {
        TODO()
//        if (!correctErrorTypes) {
//            return ifNonError()
//        }
//
//        if (type?.containsErrorTypes() == true) {
//            val typeFromSource = ktTypeProvider()?.typeElement
//            val ktFile = typeFromSource?.containingKtFile
//            if (ktFile != null) {
//                @Suppress("UNCHECKED_CAST")
//                return ErrorTypeCorrector(this, kind, ktFile).convert(typeFromSource, emptyMap()) as T
//            }
//        }
//
//        val nonErrorType = ifNonError()
//
//        if (nonErrorType is JCFieldAccess) {
//            val qualifier = nonErrorType.selected
//            if (nonErrorType.name.toString() == NON_EXISTENT_CLASS_NAME.shortName().asString()
//                && qualifier is JCIdent
//                && qualifier.name.toString() == NON_EXISTENT_CLASS_NAME.parent().asString()
//            ) {
//                @Suppress("UNCHECKED_CAST")
//                return treeMaker.FqName("java.lang.Object") as T
//            }
//        }
//
//        return nonErrorType
    }

    private class ClassSupertypes(val superClass: JCExpression?, val interfaces: JavacList<JCExpression>)

    private fun calculateSuperTypes(clazz: PsiClass, genericType: SignatureParser.ClassGenericSignature): ClassSupertypes {
        TODO()
//        val hasSuperClass = clazz.superName != "java/lang/Object" && !clazz.isEnum
//
//        val defaultSuperTypes = ClassSupertypes(
//            if (hasSuperClass) genericType.superClass else null,
//            genericType.interfaces
//        )
//
//        if (!correctErrorTypes) {
//            return defaultSuperTypes
//        }
//
//        val declaration = kaptContext.origins[clazz]?.element as? KtClassOrObject ?: return defaultSuperTypes
//        val declarationDescriptor = kaptContext.bindingContext[BindingContext.CLASS, declaration] ?: return defaultSuperTypes
//
//        if (typeMapper.mapType(declarationDescriptor.defaultType) != Type.getObjectType(clazz.name)) {
//            return defaultSuperTypes
//        }
//
//        val (superClass, superInterfaces) = partitionSuperTypes(declaration) ?: return defaultSuperTypes
//
//        val sameSuperClassCount = (superClass == null) == (defaultSuperTypes.superClass == null)
//        val sameSuperInterfaceCount = superInterfaces.size == defaultSuperTypes.interfaces.size
//
//        if (sameSuperClassCount && sameSuperInterfaceCount) {
//            return defaultSuperTypes
//        }
//
//        class SuperTypeCalculationFailure : RuntimeException()
//
//        fun nonErrorType(ref: () -> KtTypeReference?): JCExpression {
//            assert(correctErrorTypes)
//
//            return getNonErrorType<JCExpression>(
//                ErrorUtils.createErrorType(ErrorTypeKind.ERROR_SUPER_TYPE),
//                ErrorTypeCorrector.TypeKind.SUPER_TYPE,
//                ref
//            ) { throw SuperTypeCalculationFailure() }
//        }
//
//        return try {
//            ClassSupertypes(
//                superClass?.let { nonErrorType { it } },
//                mapJList(superInterfaces) { nonErrorType { it } }
//            )
//        } catch (e: SuperTypeCalculationFailure) {
//            defaultSuperTypes
//        }
    }


    // TODO
    private fun getClassName(lightClass: KtLightClass, isDefaultImpls: Boolean, packageFqName: String): String {
        return lightClass.name!!
//        return when (descriptor) {
//            is PackageFragmentDescriptor -> {
//                val className = if (packageFqName.isEmpty()) lightClass.name else lightClass.name.drop(packageFqName.length + 1)
//                if (className.isEmpty()) throw IllegalStateException("Invalid package facade class name: ${lightClass.name}")
//                className
//            }
//
//            else -> if (isDefaultImpls) "DefaultImpls" else descriptor.name.asString()
//        }
    }

    private fun isValidQualifiedName(name: FqName) = name.pathSegments().all { isValidIdentifier(it.asString()) }

    private fun isValidIdentifier(name: String, canBeConstructor: Boolean = false): Boolean {
        if (canBeConstructor && name == "<init>") {
            return true
        }

        if (name in JAVA_KEYWORDS) return false

        if (name.isEmpty()
            || !Character.isJavaIdentifierStart(name[0])
            || name.drop(1).any { !Character.isJavaIdentifierPart(it) }
        ) {
            return false
        }

        return true
    }

    /**
     * Sort class members. If the source file for the class is unknown, just sort using name and descriptor. Otherwise:
     * - all members in the same source file as the class come first (members may come from other source files)
     * - members from the class are sorted using their position in the source file
     * - members from other source files are sorted using their name and descriptor
     *
     * More details: Class methods and fields are currently sorted at serialization (see DescriptorSerializer.sort) and at deserialization
     * (see DeserializedMemberScope.OptimizedImplementation#addMembers). Therefore, the contents of the generated stub files are sorted in
     * incremental builds but not in clean builds.
     * The consequence is that the contents of the generated stub files may not be consistent across a clean build and an incremental
     * build, making the build non-deterministic and dependent tasks run unnecessarily (see KT-40882).
     */
    private class MembersPositionComparator(val classSource: KotlinPosition?, val memberData: Map<JCTree, MemberData>) :
        Comparator<JCTree> {
        override fun compare(o1: JCTree, o2: JCTree): Int {
            val data1 = memberData.getValue(o1)
            val data2 = memberData.getValue(o2)
            classSource ?: return compareDescriptors(data1, data2)

            val position1 = data1.position
            val position2 = data2.position

            return if (position1 != null && position1.path == classSource.path) {
                if (position2 != null && position2.path == classSource.path) {
                    val positionCompare = position1.pos.compareTo(position2.pos)
                    if (positionCompare != 0) positionCompare
                    else compareDescriptors(data1, data2)
                } else {
                    -1
                }
            } else if (position2 != null && position2.path == classSource.path) {
                1
            } else {
                compareDescriptors(data1, data2)
            }
        }

        private fun compareDescriptors(m1: MemberData, m2: MemberData): Int {
            val nameComparison = m1.name.compareTo(m2.name)
            if (nameComparison != 0) return nameComparison
            return m1.descriptor.compareTo(m2.descriptor)
        }
    }
}
