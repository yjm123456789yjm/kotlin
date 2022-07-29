/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("UnstableApiUsage")

package org.jetbrains.kotlin.kapt4

import com.intellij.psi.*
import com.sun.tools.javac.code.Flags
import com.sun.tools.javac.code.TypeTag
import com.sun.tools.javac.parser.Tokens
import com.sun.tools.javac.tree.JCTree
import com.sun.tools.javac.tree.JCTree.*
import kotlinx.kapt.KaptIgnored
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.base.kapt3.KaptFlag
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.kapt3.base.javac.kaptError
import org.jetbrains.kotlin.kapt3.base.javac.reportKaptError
import org.jetbrains.kotlin.kapt3.base.stubs.KaptStubLineInformation
import org.jetbrains.kotlin.kapt3.base.stubs.KotlinPosition
import org.jetbrains.kotlin.kapt4.ErrorTypeCorrector.TypeKind.*
import org.jetbrains.kotlin.light.classes.symbol.FirLightClassForClassOrObjectSymbol
import org.jetbrains.kotlin.light.classes.symbol.FirLightClassForFacade
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.utils.addToStdlib.runUnless
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import java.io.File
import javax.lang.model.element.ElementKind
import kotlin.math.sign

context(Kapt4ContextForStubGeneration)
class Kapt4StubGenerator {
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

    private val kdocCommentKeeper = if (keepKdocComments) Kapt4KDocCommentKeeper() else null

    fun generateStubs(): Map<KtLightClass, KaptStub?> {
        return classes.associateWith { convertTopLevelClass(it) }
    }

    private fun convertTopLevelClass(lightClass: KtLightClass): KaptStub? {
//        val origin = origins[lightClass]// ?: return null // TODO: handle synthetic declarations from plugins
        val ktFile = origins[lightClass] ?: return null //origin?.element?.containingFile as? KtFile ?: return null
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

    private val allAccOpcodes = listOf(
        "ACC_PUBLIC" to Opcodes.ACC_PUBLIC,
        "ACC_PRIVATE" to Opcodes.ACC_PRIVATE,
        "ACC_PROTECTED" to Opcodes.ACC_PROTECTED,
        "ACC_STATIC" to Opcodes.ACC_STATIC,
        "ACC_FINAL" to Opcodes.ACC_FINAL,
        "ACC_SUPER" to Opcodes.ACC_SUPER,
        "ACC_SYNCHRONIZED" to Opcodes.ACC_SYNCHRONIZED,
        "ACC_OPEN" to Opcodes.ACC_OPEN,
        "ACC_TRANSITIVE" to Opcodes.ACC_TRANSITIVE,
        "ACC_VOLATILE" to Opcodes.ACC_VOLATILE,
        "ACC_BRIDGE" to Opcodes.ACC_BRIDGE,
        "ACC_STATIC_PHASE" to Opcodes.ACC_STATIC_PHASE,
        "ACC_VARARGS" to Opcodes.ACC_VARARGS,
        "ACC_TRANSIENT" to Opcodes.ACC_TRANSIENT,
        "ACC_NATIVE" to Opcodes.ACC_NATIVE,
        "ACC_INTERFACE" to Opcodes.ACC_INTERFACE,
        "ACC_ABSTRACT" to Opcodes.ACC_ABSTRACT,
        "ACC_STRICT" to Opcodes.ACC_STRICT,
        "ACC_SYNTHETIC" to Opcodes.ACC_SYNTHETIC,
        "ACC_ANNOTATION" to Opcodes.ACC_ANNOTATION,
        "ACC_ENUM" to Opcodes.ACC_ENUM,
        "ACC_MANDATED" to Opcodes.ACC_MANDATED,
        "ACC_MODULE" to Opcodes.ACC_MODULE,
        "ACC_RECORD" to Opcodes.ACC_RECORD,
        "ACC_DEPRECATED" to Opcodes.ACC_DEPRECATED,
    )

    private fun showOpcodes(flags: Int) = allAccOpcodes.filter { (flags and it.second) != 0 }.map { it.first }

    @Suppress("InconsistentCommentForJavaParameter")
    private fun convertClass(
        lightClass: PsiClass,
        lineMappings: Kapt4LineMappingCollector,
        packageFqName: String,
        isTopLevel: Boolean
    ): JCClassDecl? {
//        if (isSynthetic(lightClass.access)) return null
//        if (!checkIfValidTypeName(lightClass, Type.getObjectType(lightClass.name))) return null

        val isInnerOrNested = lightClass.parent is PsiClass
        val isNested = isInnerOrNested && lightClass.isStatic
        val isInner = isInnerOrNested && !isNested

        val flags = lightClass.accessFlags//getClassAccessFlags(lightClass, isInner)

        val metadata = when (lightClass) {
            is FirLightClassForClassOrObjectSymbol -> lightClass.kotlinOrigin?.let { metadataCalculator.calculate(it) }
            is FirLightClassForFacade -> {
                val ktFiles = lightClass.files
                when (ktFiles.size) {
                    0 -> null
                    1 -> metadataCalculator.calculate(ktFiles.single())
                    else -> metadataCalculator.calculate(ktFiles)
                }
            }

            else -> null
        }

        val isEnum = lightClass.isEnum
        val modifiers = convertModifiers(
            lightClass,
            flags.toLong(),
            if (isEnum) ElementKind.ENUM else ElementKind.CLASS,
            packageFqName,
            lightClass.annotations.toList(),
            metadata
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

        val genericType = signatureParser.parseClassSignature(lightClass)

        val enumValues: JavacList<JCTree> = mapJList(lightClass.fields) { field ->
            if (field !is PsiEnumConstant) return@mapJList null
            val constructorArguments = lightClass.constructors.firstOrNull()?.parameters?.mapNotNull { it.type as? PsiType }.orEmpty()
            val args = mapJList(constructorArguments) { convertLiteralExpression(lightClass, getDefaultValue(it)) }

            convertField(
                field, lightClass, lineMappings, packageFqName, treeMaker.NewClass(
                    /* enclosing = */ null,
                    /* typeArgs = */ JavacList.nil(),
                    /* lightClass = */ treeMaker.Ident(treeMaker.name(field.name)),
                    /* args = */ args,
                    /* def = */ null
                )
            )
        }

        val fieldsPositions = mutableMapOf<JCTree, MemberData>()
        val fields = mapJList<PsiField, JCTree>(lightClass.fields) { field ->
            runUnless(field is PsiEnumConstant) { convertField(field, lightClass, lineMappings, packageFqName)?.also {
                    fieldsPositions[it] = MemberData(field.name, field.signature, lineMappings.getPosition(lightClass, field))
                }
            }
        }

        val methodsPositions = mutableMapOf<JCTree, MemberData>()
        val methods = mapJList<PsiMethod, JCTree>(lightClass.methods) { method ->
            if (isEnum && method.isSyntheticStaticEnumMethod()) {
                return@mapJList null
            }

            convertMethod(method, lightClass, lineMappings, packageFqName, isInner)?.also {
                methodsPositions[it] = MemberData(method.name, method.signature, lineMappings.getPosition(lightClass, method))
            }
        }

        val nestedClasses = mapJList(lightClass.allInnerClasses) { innerClass ->
            convertClass(innerClass, lineMappings, packageFqName, false)
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
            JavacList.from(enumValues + sortedFields + sortedMethods + nestedClasses)
        ).keepKdocCommentsIfNecessary(lightClass)
    }

    private fun PsiMethod.isSyntheticStaticEnumMethod(): Boolean {
        if (!this.isStatic) return false
        return when (name) {
            StandardNames.ENUM_VALUES.asString() -> parameters.isEmpty()
            StandardNames.ENUM_VALUE_OF.asString() -> (parameters.singleOrNull()?.type as? PsiClassType)?.qualifiedName == "java.lang.String"
            else -> false
        }
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
    private fun getClassAccessFlags(lightClass: PsiClass, isNested: Boolean): Int {
        val parentClass = lightClass.parent as? PsiClass

        var access = lightClass.accessFlags
        access = access or when {
            lightClass.isRecord -> Opcodes.ACC_RECORD
            lightClass.isInterface -> Opcodes.ACC_INTERFACE
            lightClass.isEnum -> Opcodes.ACC_ENUM
            else -> 0
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
        if (lightClass.isAnnotationType) {
            access = access or Opcodes.ACC_ANNOTATION
        }
        return access
    }

    private fun convertMetadataAnnotation(metadata: Metadata): JCAnnotation {
        val argumentsWithNames = mapOf(
            "k" to metadata.kind,
            "mv" to metadata.metadataVersion.toList(),
            "bv" to metadata.bytecodeVersion.toList(),
            "d1" to metadata.data1.toList(),
            "d2" to metadata.data2.toList(),
            "xs" to metadata.extraString,
            "pn" to metadata.packageName,
            "xi" to metadata.extraInt,
        )
        val arguments = argumentsWithNames.map { (name, value) ->
            val jValue = convertLiteralExpression(containingClass = null, value)
            treeMaker.Assign(treeMaker.SimpleName(name), jValue)
        }
        return treeMaker.Annotation(treeMaker.FqName(Metadata::class.java.canonicalName), JavacList.from(arguments))
    }

    // TODO
    private fun convertAnnotation(
        containingClass: PsiClass,
        annotation: PsiAnnotation,
        packageFqName: String? = "",
        filtered: Boolean = true
    ): JCAnnotation? {
        val rawQualifiedName = annotation.qualifiedName ?: return null
        val fqName = treeMaker.getQualifiedName(rawQualifiedName)
        if (filtered) {
            if (BLACKLISTED_ANNOTATIONS.any { fqName.startsWith(it) }) return null
            if (stripMetadata && fqName == KOTLIN_METADATA_ANNOTATION) return null
        }
//        TODO()
//        val ktAnnotation = annotationDescriptor?.source?.getPsi() as? KtAnnotationEntry
        val annotationFqName = getNonErrorType(
            annotation.resolveAnnotationType()?.defaultType,
            ANNOTATION,
            { TODO()/*ktAnnotation?.typeReference*/ },
            {
                val useSimpleName = '.' in fqName && fqName.substringBeforeLast('.', "") == packageFqName

                when {
                    useSimpleName -> treeMaker.FqName(fqName.substring(packageFqName!!.length + 1))
                    else -> treeMaker.FqName(fqName)
                }
            }
        )

        val values = mapJList<_, JCExpression>(annotation.parameterList.attributes) {
            val expr = when (val value = it.value) {
                is PsiLiteral -> convertLiteralExpression(containingClass = null, value.value)
                is PsiArrayInitializerExpression -> {
                    val arguments = mapJList(value.initializers) { convertLiteralExpression(containingClass = null, it) }
                    treeMaker.NewArray(null, null, arguments)
                }

                else -> treeMaker.SimpleName("stub")
            }
            treeMaker.Assign(treeMaker.SimpleName(it.name ?: NO_NAME_PROVIDED), expr)
        }

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
        return treeMaker.Annotation(annotationFqName, values)
    }

    private fun convertModifiers(
        containingClass: PsiClass,
        access: Int,
        kind: ElementKind,
        packageFqName: String,
        allAnnotations: List<PsiAnnotation>,
        metadata: Metadata?
    ): JCModifiers {
        return convertModifiers(containingClass, access.toLong(), kind, packageFqName, allAnnotations, metadata)
    }

    private fun convertModifiers(
        containingClass: PsiClass,
        access: Long,
        kind: ElementKind,
        packageFqName: String,
        allAnnotations: List<PsiAnnotation>,
        metadata: Metadata?
    ): JCModifiers {
        var seenOverride = false
        fun convertAndAdd(list: JavacList<JCAnnotation>, annotation: PsiAnnotation): JavacList<JCAnnotation> {
            if (annotation.hasQualifiedName("java.lang.Override")) {
                if (seenOverride) return list  // KT-34569: skip duplicate @Override annotations
                seenOverride = true
            }
            val annotationTree = convertAnnotation(containingClass, annotation, packageFqName) ?: return list
            return list.prepend(annotationTree)
        }

        var annotations = allAnnotations.fold(JavacList.nil(), ::convertAndAdd)

        if (isDeprecated(access)) {
            val type = treeMaker.RawType(Type.getType(java.lang.Deprecated::class.java))
            annotations = annotations.append(treeMaker.Annotation(type, JavacList.nil()))
        }
        if (metadata != null) {
            annotations = annotations.prepend(convertMetadataAnnotation(metadata))
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

    class KaptStub(val file: JCCompilationUnit, private val kaptMetadata: ByteArray? = null) {
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
        containingClass: PsiClass,
        lineMappings: Kapt4LineMappingCollector,
        packageFqName: String,
        explicitInitializer: JCExpression? = null
    ): JCVariableDecl? {
//        if (field.isSynthetic || isIgnored(field.invisibleAnnotations)) return null // TODO
        // not needed anymore

        val fieldAnnotations = field.annotations.asList()

        if (isIgnored(fieldAnnotations)) return null

//        val fieldAnnotations = when {
//            !isIrBackend && descriptor is PropertyDescriptor -> descriptor.backingField?.annotations
//            else -> descriptor?.annotations
//        } ?: Annotations.EMPTY

        val access = field.accessFlags
        val modifiers = convertModifiers(
            containingClass,
            access, ElementKind.FIELD, packageFqName,
            fieldAnnotations,
            metadata = null
        )

        val name = field.name
        if (!isValidIdentifier(name)) return null

        val type = field.type

        // TODO
//        if (!checkIfValidTypeName(containingClass, type)) {
//            return null
//        }

//        fun typeFromAsm() = signatureParser.parseFieldSignature(field.signature, treeMaker.RawType(type))

        // Enum type must be an identifier (Javac requirement)
        val typeExpression = if (isEnum(access)) {
            treeMaker.SimpleName(treeMaker.getQualifiedName(type).substringAfterLast('.'))
        } else {
            treeMaker.TypeWithArguments(type)
        }

        lineMappings.registerField(containingClass, field)

        val initializer = explicitInitializer ?: convertPropertyInitializer(containingClass, field)
        return treeMaker.VarDef(modifiers, treeMaker.name(name), typeExpression, initializer) // TODO: .keepKdocCommentsIfNecessary(field)
    }

    private fun convertPropertyInitializer(containingClass: PsiClass, field: PsiField): JCExpression? {
        val origin = field.ktOrigin

        val propertyInitializer = field.initializer

//        if (propertyInitializer is PsiEnumConstant) {
//            if (propertyInitializer != null) {
//                return convertConstantValueArguments(containingClass, value, listOf(propertyInitializer))
//            }
//
//            return convertValueOfPrimitiveTypeOrString(value)
//        }
//
//        val propertyType = (origin?.descriptor as? PropertyDescriptor)?.returnType
//
//        /*
//            Work-around for enum classes in companions.
//            In expressions "Foo.Companion.EnumClass", Java prefers static field over a type name, making the reference invalid.
//        */
//        if (propertyType != null && propertyType.isEnum()) {
//            val enumClass = propertyType.constructor.declarationDescriptor
//            if (enumClass is ClassDescriptor && enumClass.isInsideCompanionObject()) {
//                return null
//            }
//        }
//
//        if (propertyInitializer != null && propertyType != null) {
//            val constValue = getConstantValue(propertyInitializer, propertyType)
//            if (constValue != null) {
//                val asmValue = mapConstantValueToAsmRepresentation(constValue)
//                if (asmValue !== UnknownConstantValue) {
//                    return convertConstantValueArguments(containingClass, asmValue, listOf(propertyInitializer))
//                }
//            }
//        }
//
        if (field.isFinal) {
            val type = field.type
            return if (propertyInitializer is PsiLiteralExpression) {
                val rawValue = propertyInitializer.value
                val rawNumberValue = rawValue as? Number
                val actualValue = when (type) {
                    PsiType.BYTE -> rawNumberValue?.toByte()
                    PsiType.SHORT -> rawNumberValue?.toShort()
                    PsiType.INT -> rawNumberValue?.toInt()
                    PsiType.LONG -> rawNumberValue?.toLong()
                    PsiType.FLOAT -> rawNumberValue?.toFloat()
                    PsiType.DOUBLE -> rawNumberValue?.toDouble()
                    PsiType.CHAR -> rawNumberValue?.toChar()
                    else -> null
                } ?: rawValue
                convertValueOfPrimitiveTypeOrString(actualValue)
            } else {
                convertLiteralExpression(containingClass, getDefaultValue(type))
            }
        }

        return null
    }

    private fun convertLiteralExpression(containingClass: PsiClass?, value: Any?): JCExpression {
        fun convertDeeper(value: Any?) = convertLiteralExpression(containingClass, value)

        convertValueOfPrimitiveTypeOrString(value)?.let { return it }

        return when (value) {
            null -> treeMaker.Literal(TypeTag.BOT, null)

            is ByteArray -> treeMaker.NewArray(null, JavacList.nil(), mapJList(value.asIterable(), ::convertDeeper))
            is BooleanArray -> treeMaker.NewArray(null, JavacList.nil(), mapJList(value.asIterable(), ::convertDeeper))
            is CharArray -> treeMaker.NewArray(null, JavacList.nil(), mapJList(value.asIterable(), ::convertDeeper))
            is ShortArray -> treeMaker.NewArray(null, JavacList.nil(), mapJList(value.asIterable(), ::convertDeeper))
            is IntArray -> treeMaker.NewArray(null, JavacList.nil(), mapJList(value.asIterable(), ::convertDeeper))
            is LongArray -> treeMaker.NewArray(null, JavacList.nil(), mapJList(value.asIterable(), ::convertDeeper))
            is FloatArray -> treeMaker.NewArray(null, JavacList.nil(), mapJList(value.asIterable(), ::convertDeeper))
            is DoubleArray -> treeMaker.NewArray(null, JavacList.nil(), mapJList(value.asIterable(), ::convertDeeper))
            is Array<*> -> { // Two-element String array for enumerations ([desc, fieldName])
                assert(value.size == 2)
                val enumType = Type.getType(value[0] as String)
                val valueName = (value[1] as String).takeIf { isValidIdentifier(it) } ?: run {
                    compiler.log.report(kaptError("'${value[1]}' is an invalid Java enum value name"))
                    "InvalidFieldName"
                }

                treeMaker.Select(treeMaker.RawType(enumType), treeMaker.name(valueName))
            }

            is List<*> -> treeMaker.NewArray(null, JavacList.nil(), mapJList(value, ::convertDeeper))

//            is Type -> {
//                checkIfValidTypeName(containingClass, value)
//                treeMaker.Select(treeMaker.Type(value), treeMaker.name("class"))
//            }
//
//            is AnnotationNode -> convertAnnotation(containingClass, value, packageFqName = null, filtered = false)!!
            else -> throw IllegalArgumentException("Illegal literal expression value: $value (${value::class.java.canonicalName})")
        }
    }


    private fun getDefaultValue(type: PsiType): Any? = when (type) {
        PsiType.BYTE -> 0
        PsiType.BOOLEAN -> false
        PsiType.CHAR -> '\u0000'
        PsiType.SHORT -> 0
        PsiType.INT -> 0
        PsiType.LONG -> 0L
        PsiType.FLOAT -> 0.0F
        PsiType.DOUBLE -> 0.0
        else -> null
    }

    private fun convertValueOfPrimitiveTypeOrString(value: Any?): JCExpression? {
        fun specialFpValueNumerator(value: Double): Double = if (value.isNaN()) 0.0 else 1.0 * value.sign
        val convertedValue = when (value) {
            is Char -> treeMaker.Literal(TypeTag.CHAR, value.code)
            is Byte -> treeMaker.TypeCast(treeMaker.TypeIdent(TypeTag.BYTE), treeMaker.Literal(TypeTag.INT, value.toInt()))
            is Short -> treeMaker.TypeCast(treeMaker.TypeIdent(TypeTag.SHORT), treeMaker.Literal(TypeTag.INT, value.toInt()))
            is Boolean, is Int, is Long, is String -> treeMaker.Literal(value)
            is Float -> when {
                value.isFinite() -> treeMaker.Literal(value)
                else -> treeMaker.Binary(
                    Tag.DIV,
                    treeMaker.Literal(specialFpValueNumerator(value.toDouble()).toFloat()),
                    treeMaker.Literal(0.0F)
                )
            }

            is Double -> when {
                value.isFinite() -> treeMaker.Literal(value)
                else -> treeMaker.Binary(Tag.DIV, treeMaker.Literal(specialFpValueNumerator(value)), treeMaker.Literal(0.0))
            }

            else -> null
        }

        return convertedValue
    }


    private fun convertMethod(
        method: PsiMethod,
        containingClass: PsiClass,
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

        val isConstructor = method.isConstructor

        val name = method.name
        if (!isValidIdentifier(name, canBeConstructor = isConstructor)) return null

        val modifiers = convertModifiers(
            containingClass,
            if (containingClass.isEnum && isConstructor)
                (method.accessFlags.toLong() and VISIBILITY_MODIFIERS.inv())
            else
                method.accessFlags.toLong(),
            ElementKind.METHOD, packageFqName, visibleAnnotations,
            metadata = null
        )

        if (containingClass.isInterface && !method.isAbstract && !method.isStatic) {
            modifiers.flags = modifiers.flags or Flags.DEFAULT
        }

        val returnType = method.returnType ?: PsiType.VOID

        val parametersInfo = method.getParametersInfo(containingClass, isInner)

        if (!checkIfValidTypeName(containingClass, returnType)
            || parametersInfo.any { !checkIfValidTypeName(containingClass, it.type) }
        ) {
            return null
        }

        @Suppress("NAME_SHADOWING")
        val jParameters = mapJListIndexed(parametersInfo) { index, info ->
            val lastParameter = index == parametersInfo.lastIndex
            val isArrayType = info.type is PsiArrayType

            val varargs = if (lastParameter && isArrayType && method.hasVarargs) Flags.VARARGS else 0L
            val modifiers = convertModifiers(
                containingClass,
                info.flags or varargs or Flags.PARAMETER,
                ElementKind.PARAMETER,
                packageFqName,
                info.visibleAnnotations + info.invisibleAnnotations, // TODO
                metadata = null
            )

            val name = info.name.takeIf { isValidIdentifier(it) } ?: "p$index"
            val type = treeMaker.TypeWithArguments(info.type)
            treeMaker.VarDef(modifiers, treeMaker.name(name), type, null)
        }
        val jTypeParameters = mapJList(method.typeParameters) { signatureParser.convertTypeParameter(it) }
        val jExceptionTypes = mapJList(method.throwsTypes) { treeMaker.TypeWithArguments(it as PsiType) }
        val jReturnType = if (isConstructor) null else treeMaker.TypeWithArguments(returnType) // TODO: handle error type

        val defaultValue = null //method.annotationDefault?.let { convertLiteralExpression(containingClass, it) }

        val body = if (defaultValue != null) {
            null
        } else if (method.isAbstract) {
            null
        } else if (isConstructor && containingClass.isEnum) {
            treeMaker.Block(0, JavacList.nil())
        } else if (isConstructor) {
            val superConstructor = containingClass.superClass?.constructors?.firstOrNull { !it.isPrivate }
            val superClassConstructorCall = if (superConstructor != null) {
                val args = mapJList(superConstructor.parameterList.parameters) { param ->
                    convertLiteralExpression(containingClass, getDefaultValue(param.type))
                }
                val call = treeMaker.Apply(JavacList.nil(), treeMaker.SimpleName("super"), args)
                JavacList.of<JCStatement>(treeMaker.Exec(call))
            } else {
                JavacList.nil()
            }
            treeMaker.Block(0, superClassConstructorCall)
        } else if (returnType == PsiType.VOID) {
            treeMaker.Block(0, JavacList.nil())
        } else {
            val returnStatement = treeMaker.Return(convertLiteralExpression(containingClass, getDefaultValue(returnType)))
            treeMaker.Block(0, JavacList.of(returnStatement))
        }

        lineMappings.registerMethod(containingClass, method)

        return treeMaker.MethodDef(
            modifiers, treeMaker.name(name), jReturnType, jTypeParameters,
            jParameters, jExceptionTypes,
            body, defaultValue
        ).keepSignature(lineMappings, method).keepKdocCommentsIfNecessary(method)
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

    private tailrec fun checkIfValidTypeName(containingClass: PsiClass, type: PsiType): Boolean {
        when (type) {
            is PsiArrayType -> return checkIfValidTypeName(containingClass, type.componentType)
            is PsiPrimitiveType -> return true
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

        val clazz = type.resolvedClass ?: return true

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
//        TODO()
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
        if (!correctErrorTypes) {
            return ifNonError()
        }

//        TODO
//        if (type?.containsErrorTypes() == true) {
//            val typeFromSource = ktTypeProvider()?.typeElement
//            val ktFile = typeFromSource?.containingKtFile
//            if (ktFile != null) {
//                @Suppress("UNCHECKED_CAST")
//                return ErrorTypeCorrector(this, kind, ktFile).convert(typeFromSource, emptyMap()) as T
//            }
//        }
//
        val nonErrorType = ifNonError()

        if (nonErrorType is JCFieldAccess) {
            val qualifier = nonErrorType.selected
            if (nonErrorType.name.toString() == NON_EXISTENT_CLASS_NAME.shortName().asString()
                && qualifier is JCIdent
                && qualifier.name.toString() == NON_EXISTENT_CLASS_NAME.parent().asString()
            ) {
                @Suppress("UNCHECKED_CAST")
                return treeMaker.FqName("java.lang.Object") as T
            }
        }

        return nonErrorType
    }

    private class ClassSupertypes(val superClass: JCExpression?, val interfaces: JavacList<JCExpression>)

    private fun calculateSuperTypes(clazz: PsiClass, genericType: SignatureParser.ClassGenericSignature): ClassSupertypes {
        val superClass = clazz.superClass

        val hasSuperClass = superClass?.qualifiedName != "java.lang.Object" && !clazz.isEnum

        val defaultSuperTypes = ClassSupertypes(
            if (hasSuperClass) genericType.superClass else null,
            genericType.interfaces
        )

        if (!correctErrorTypes) {
            return defaultSuperTypes
        }

        val superInterfaces = clazz.supers.filter { it.isInterface }

//        TODO
//
//        if (typeMapper.mapType(declarationDescriptor.defaultType) != Type.getObjectType(clazz.name)) {
//            return defaultSuperTypes
//        }
//
        val sameSuperClassCount = (superClass == null) == (defaultSuperTypes.superClass == null)
        val sameSuperInterfaceCount = superInterfaces.size == defaultSuperTypes.interfaces.size

        if (sameSuperClassCount && sameSuperInterfaceCount) {
            return defaultSuperTypes
        }

        class SuperTypeCalculationFailure : RuntimeException()

        fun nonErrorType(ref: () -> PsiClass?): JCExpression {
            TODO()
//            assert(correctErrorTypes)
//
//            return getNonErrorType<JCExpression>(
//                ErrorUtils.createErrorType(ErrorTypeKind.ERROR_SUPER_TYPE),
//                ErrorTypeCorrector.TypeKind.SUPER_TYPE,
//                ref
//            ) { throw SuperTypeCalculationFailure() }
        }

        return try {
            ClassSupertypes(
                superClass?.let { nonErrorType { it } },
                mapJList(superInterfaces) { nonErrorType { it } }
            )
        } catch (e: SuperTypeCalculationFailure) {
            defaultSuperTypes
        }
    }


    // TODO
    private fun getClassName(lightClass: PsiClass, isDefaultImpls: Boolean, packageFqName: String): String {
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
