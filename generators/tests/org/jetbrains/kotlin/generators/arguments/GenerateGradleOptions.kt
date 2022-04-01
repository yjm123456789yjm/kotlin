/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.generators.arguments

import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.Printer
import java.io.File
import java.io.PrintStream
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.withNullability

// Additional properties that should be included in interface
@Suppress("unused")
interface AdditionalGradleProperties {
    @GradleOption(
        value = EmptyList::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(value = "", description = "A list of additional compiler arguments")
    var freeCompilerArgs: List<String>

    object EmptyList : DefaultValues("emptyList()")
}

private const val GRADLE_API_SRC_DIR = "libraries/tools/kotlin-gradle-plugin-api/src/main/kotlin"
private const val GRADLE_PLUGIN_SRC_DIR = "libraries/tools/kotlin-gradle-plugin/src/main/kotlin"
private const val OPTIONS_PACKAGE_PREFIX = "org.jetbrains.kotlin.gradle.dsl"
private const val IMPLEMENTATION_SUFFIX = "Base"

fun generateKotlinGradleOptions(withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit) {
    val apiSrcDir = File(GRADLE_API_SRC_DIR)
    val srcDir = File(GRADLE_PLUGIN_SRC_DIR)

    val (commonToolInterfaceFqName, commonToolOptions) = generateKotlinCommonToolOptions(apiSrcDir, withPrinterToFile)
    val (commonCompilerInterfaceFqName, commonCompilerOptions) = generateKotlinCommonOptions(
        apiSrcDir,
        commonToolInterfaceFqName,
        withPrinterToFile
    )

    val (jvmInterfaceFqName, jvmOptions) = generateKotlinJvmOptions(
        apiSrcDir,
        commonCompilerInterfaceFqName,
        withPrinterToFile
    )
    generateKotlinJvmOptionsImpl(
        srcDir,
        jvmInterfaceFqName,
        commonToolOptions + commonCompilerOptions + jvmOptions,
        withPrinterToFile
    )

    val (jsInterfaceFqName, jsOptions) = generateKotlinJsOptions(
        apiSrcDir,
        commonCompilerInterfaceFqName,
        withPrinterToFile
    )
    generateKotlinJsOptionsImpl(
        srcDir,
        jsInterfaceFqName,
        commonToolOptions + commonCompilerOptions + jsOptions,
        withPrinterToFile
    )

    val (jsDceInterfaceName, jsDceOptions) = generateJsDceOptions(
        apiSrcDir,
        commonToolInterfaceFqName,
        withPrinterToFile
    )
    generateJsDceOptionsImpl(
        srcDir,
        jsDceInterfaceName,
        commonToolOptions + jsDceOptions,
        withPrinterToFile
    )

    val (multiplatformCommonInterfaceFqName, multiplatformCommonOptions) = generateMultiplatformCommonOptions(
        apiSrcDir,
        commonCompilerInterfaceFqName,
        withPrinterToFile
    )
    generateMultiplatformCommonOptionsImpl(
        srcDir,
        multiplatformCommonInterfaceFqName,
        commonToolOptions + commonCompilerOptions + multiplatformCommonOptions,
        withPrinterToFile
    )
}

fun main() {
    fun getPrinter(file: File, fn: Printer.() -> Unit) {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        PrintStream(file.outputStream()).use {
            val printer = Printer(it)
            printer.fn()
        }
    }

    generateKotlinGradleOptions(::getPrinter)
}

//region Option Generators

private fun generateKotlinCommonToolOptions(
    apiSrcDir: File,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
): Pair<FqName, List<KProperty1<*, *>>> {
    val commonInterfaceFqName = FqName("$OPTIONS_PACKAGE_PREFIX.KotlinCommonToolOptions")
    val commonOptions = gradleOptions<CommonToolArguments>()
    val additionalOptions = gradleOptions<AdditionalGradleProperties>()
    withPrinterToFile(fileFromFqName(apiSrcDir, commonInterfaceFqName)) {
        generateInterface(
            commonInterfaceFqName,
            commonOptions + additionalOptions
        )
    }

    println("### Attributes common for JVM, JS, and JS DCE\n")
    generateMarkdown(commonOptions + additionalOptions)

    return commonInterfaceFqName to (commonOptions + additionalOptions)
}

private fun generateKotlinCommonOptions(
    apiSrcDir: File,
    commonInterfaceFqName: FqName,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
): Pair<FqName, List<KProperty1<*, *>>> {
    val commonCompilerInterfaceFqName = FqName("$OPTIONS_PACKAGE_PREFIX.KotlinCommonOptions")
    val commonCompilerOptions = gradleOptions<CommonCompilerArguments>()
    withPrinterToFile(fileFromFqName(apiSrcDir, commonCompilerInterfaceFqName)) {
        generateInterface(
            commonCompilerInterfaceFqName,
            commonCompilerOptions,
            parentType = commonInterfaceFqName
        )
    }

    println("\n### Attributes common for JVM and JS\n")
    generateMarkdown(commonCompilerOptions)

    return commonCompilerInterfaceFqName to commonCompilerOptions
}

private fun generateKotlinJvmOptions(
    apiSrcDir: File,
    commonCompilerInterfaceFqName: FqName,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
): Pair<FqName, List<KProperty1<*, *>>> {
    val jvmInterfaceFqName = FqName("$OPTIONS_PACKAGE_PREFIX.KotlinJvmOptions")
    val jvmOptions = gradleOptions<K2JVMCompilerArguments>()
    withPrinterToFile(fileFromFqName(apiSrcDir, jvmInterfaceFqName)) {
        generateInterface(
            jvmInterfaceFqName,
            jvmOptions,
            parentType = commonCompilerInterfaceFqName,
            addDslInterface = true
        )
    }

    println("\n### Attributes specific for JVM\n")
    generateMarkdown(jvmOptions)

    return jvmInterfaceFqName to jvmOptions
}

private fun generateKotlinJvmOptionsImpl(
    srcDir: File,
    jvmInterfaceFqName: FqName,
    allJvmOptions: List<KProperty1<*, *>>,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
) {
    val k2JvmCompilerArgumentsFqName = FqName(K2JVMCompilerArguments::class.qualifiedName!!)
    val jvmImplFqName = FqName("${jvmInterfaceFqName.asString()}$IMPLEMENTATION_SUFFIX")
    withPrinterToFile(fileFromFqName(srcDir, jvmImplFqName)) {
        generateImpl(
            jvmImplFqName,
            jvmInterfaceFqName,
            k2JvmCompilerArgumentsFqName,
            allJvmOptions
        )
    }
}

private fun generateKotlinJsOptions(
    apiSrcDir: File,
    commonCompilerInterfaceFqName: FqName,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
): Pair<FqName, List<KProperty1<*, *>>> {
    val jsInterfaceFqName = FqName("$OPTIONS_PACKAGE_PREFIX.KotlinJsOptions")
    val jsOptions = gradleOptions<K2JSCompilerArguments>()
    withPrinterToFile(fileFromFqName(apiSrcDir, jsInterfaceFqName)) {
        generateInterface(
            jsInterfaceFqName,
            jsOptions,
            parentType = commonCompilerInterfaceFqName,
            addDslInterface = true
        )
    }

    println("\n### Attributes specific for JS\n")
    generateMarkdown(jsOptions)

    return jsInterfaceFqName to jsOptions
}

private fun generateKotlinJsOptionsImpl(
    srcDir: File,
    jsInterfaceFqName: FqName,
    jsOptions: List<KProperty1<*, *>>,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
) {
    val k2JsCompilerArgumentsFqName = FqName(K2JSCompilerArguments::class.qualifiedName!!)
    val jsImplFqName = FqName("${jsInterfaceFqName.asString()}$IMPLEMENTATION_SUFFIX")
    withPrinterToFile(fileFromFqName(srcDir, jsImplFqName)) {
        generateImpl(
            jsImplFqName,
            jsInterfaceFqName,
            k2JsCompilerArgumentsFqName,
            jsOptions
        )
    }
}

private fun generateJsDceOptions(
    apiSrcDir: File,
    commonInterfaceFqName: FqName,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
): Pair<FqName, List<KProperty1<*, *>>> {
    val jsDceInterfaceFqName = FqName("$OPTIONS_PACKAGE_PREFIX.KotlinJsDceOptions")
    val jsDceOptions = gradleOptions<K2JSDceArguments>()
    withPrinterToFile(fileFromFqName(apiSrcDir, jsDceInterfaceFqName)) {
        generateInterface(
            jsDceInterfaceFqName,
            jsDceOptions,
            parentType = commonInterfaceFqName,
            addDslInterface = true
        )
    }

    println("\n### Attributes specific for JS/DCE\n")
    generateMarkdown(jsDceOptions)

    return jsDceInterfaceFqName to jsDceOptions
}

private fun generateJsDceOptionsImpl(
    srcDir: File,
    jsDceInterfaceFqName: FqName,
    jsDceOptions: List<KProperty1<*, *>>,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
) {
    val k2JsDceArgumentsFqName = FqName(K2JSDceArguments::class.qualifiedName!!)
    val jsDceImplFqName = FqName("${jsDceInterfaceFqName.asString()}$IMPLEMENTATION_SUFFIX")
    withPrinterToFile(fileFromFqName(srcDir, jsDceImplFqName)) {
        generateImpl(
            jsDceImplFqName,
            jsDceInterfaceFqName,
            k2JsDceArgumentsFqName,
            jsDceOptions
        )
    }
}

private fun generateMultiplatformCommonOptions(
    apiSrcDir: File,
    commonCompilerInterfaceFqName: FqName,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
): Pair<FqName, List<KProperty1<*, *>>> {
    val multiplatformCommonInterfaceFqName = FqName("$OPTIONS_PACKAGE_PREFIX.KotlinMultiplatformCommonOptions")
    val multiplatformCommonOptions = gradleOptions<K2MetadataCompilerArguments>()
    withPrinterToFile(fileFromFqName(apiSrcDir, multiplatformCommonInterfaceFqName)) {
        generateInterface(
            multiplatformCommonInterfaceFqName,
            multiplatformCommonOptions,
            parentType = commonCompilerInterfaceFqName,
            addDslInterface = true
        )
    }

    println("\n### Attributes specific for Multiplatform/Common\n")
    generateMarkdown(multiplatformCommonOptions)

    return multiplatformCommonInterfaceFqName to multiplatformCommonOptions
}

private fun generateMultiplatformCommonOptionsImpl(
    srcDir: File,
    multiplatformCommonInterfaceFqName: FqName,
    multiplatformCommonOptions: List<KProperty1<*, *>>,
    withPrinterToFile: (targetFile: File, Printer.() -> Unit) -> Unit
) {
    val k2metadataCompilerArgumentsFqName = FqName(K2MetadataCompilerArguments::class.qualifiedName!!)
    val multiplatformCommonImplFqName = FqName("${multiplatformCommonInterfaceFqName.asString()}$IMPLEMENTATION_SUFFIX")
    withPrinterToFile(fileFromFqName(srcDir, multiplatformCommonImplFqName)) {
        generateImpl(
            multiplatformCommonImplFqName,
            multiplatformCommonInterfaceFqName,
            k2metadataCompilerArgumentsFqName,
            multiplatformCommonOptions
        )
    }
}

//endregion

//region Helper functions

private inline fun <reified T : Any> List<KProperty1<T, *>>.filterToBeDeleted() = filter { prop ->
    prop.findAnnotation<GradleDeprecatedOption>()
        ?.let { LanguageVersion.fromVersionString(it.removeAfter) }
        ?.let { it >= LanguageVersion.LATEST_STABLE }
        ?: true
}

private inline fun <reified T : Any> gradleOptions(): List<KProperty1<T, *>> =
    T::class
        .declaredMemberProperties
        .filter {
            it.findAnnotation<GradleOption>() != null
        }
        .filterToBeDeleted()
        .sortedBy { it.name }

private fun fileFromFqName(baseDir: File, fqName: FqName): File {
    val fileRelativePath = fqName.asString().replace(".", "/") + ".kt"
    return File(baseDir, fileRelativePath)
}

private fun Printer.generateInterface(
    type: FqName,
    properties: List<KProperty1<*, *>>,
    parentType: FqName? = null,
    addDslInterface: Boolean = false,
) {
    val afterType = parentType?.let { " : $it" }
    generateDeclaration("interface", type, afterType = afterType) {
        for (property in properties) {
            println()
            generateDoc(property)
            generateOptionDeprecation(property)
            generatePropertyProvider(property)
        }

        if (addDslInterface) {
            println()
            println("interface ${type.shortName()}Dsl : KotlinOptionsDsl<${type.shortName()}> {")
            withIndent {
                properties.forEach {
                    println()
                    generateDoc(it)
                    generateOptionDeprecation(it)
                    generatePropertyGetterAndSetter(it)
                }
            }
            println("}")
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun Printer.generateImpl(
    type: FqName,
    parentType: FqName,
    argsType: FqName,
    properties: List<KProperty1<*, *>>
) {
    generateDeclaration(
        "internal class",
        type,
        constructorDeclaration = "@javax.inject.Inject constructor(\n    objectFactory: org.gradle.api.model.ObjectFactory\n)",
        afterType = ": $parentType"
    ) {
        for (property in properties) {
            println()
            generatePropertyProviderImpl(property)
        }

        println()
        println("internal fun toCompilerArguments(args: $argsType) {")
        withIndent {
            for (property in properties) {
                if (property.name != "freeCompilerArgs") {
                    val getter = if (property.gradleReturnType.endsWith("?")) ".orNull" else ".get()"
                    println("args.${property.name} = ${property.name}$getter")
                } else {
                    println("args.freeArgs += ${property.name}.get()")
                }
            }

            addAdditionalJvmArgs(type)
        }
        println("}")

        println()
        println("internal fun fillDefaultValues(args: $argsType) {")
        withIndent {
            properties
                .filter { it.name != "freeCompilerArgs" }
                .forEach {
                    println("args.${it.name} = ${it.gradleDefaultValue}")
                }

            addAdditionalJvmArgs(type)
        }
        println("}")
    }
}

private fun Printer.addAdditionalJvmArgs(implType: FqName) {
    // Adding required 'noStdlib' and 'noReflect' compiler arguments for JVM compilation
    // Otherwise compilation via build tools will fail
    if (implType.shortName().toString() == "KotlinJvmOptionsBase") {
        println()
        println("// Arguments with always default values when used from build tools")
        println("args.noStdlib = true")
        println("args.noReflect = true")
    }
}

private fun Printer.generateDeclaration(
    modifiers: String,
    type: FqName,
    constructorDeclaration: String? = null,
    afterType: String? = null,
    generateBody: Printer.() -> Unit
) {
    println(
        """
        // DO NOT EDIT MANUALLY!
        // Generated by org/jetbrains/kotlin/generators/arguments/GenerateGradleOptions.kt
        // To regenerate run 'generateGradleOptions' task
        @file:Suppress("RemoveRedundantQualifierName", "Deprecation", "DuplicatedCode")
        
        """.trimIndent()
    )

    if (!type.parent().isRoot) {
        println("package ${type.parent()}")
        println()
    }
    print("$modifiers ${type.shortName()}")
    constructorDeclaration?.let { print(" $it ") }
    afterType?.let { print("$afterType ") }
    println("{")
    withIndent {
        generateBody()
    }
    println("}")
}

private fun Printer.generatePropertyProvider(
    property: KProperty1<*, *>,
    modifiers: String = ""
) {
    if (property.gradleDefaultValue == "null" &&
        property.gradleInputType == GradleInputTypes.INPUT
    ) {
        println("@get:org.gradle.api.tasks.Optional")
    }
    println("@get:${property.gradleInputType}")
    println("${modifiers.appendWhitespaceIfNotBlank}val ${property.name}: ${property.gradleLazyReturnType}")
}

private fun Printer.generatePropertyProviderImpl(
    property: KProperty1<*, *>,
    modifiers: String = ""
) {
    println(
        "override ${modifiers.appendWhitespaceIfNotBlank}val ${property.name}: ${property.gradleLazyReturnType} ="
    )
    withIndent {
        val convention = if (property.gradleDefaultValue != "null") {
            ".convention(${property.gradleDefaultValue})"
        } else {
            ""
        }

        println(
            "objectFactory${property.gradleLazyReturnTypeInstantiator}$convention"
        )
    }
}

private fun Printer.generatePropertyGetterAndSetter(
    property: KProperty1<*, *>,
    modifiers: String = "",
) {
    val returnType = property.gradleReturnType

    println("@get:org.gradle.api.tasks.Internal")
    println("${modifiers.appendWhitespaceIfNotBlank}var ${property.name}: $returnType")
    val getter = if (returnType.endsWith("?")) ".orNull" else ".get()"
    withIndent {
        println("get() = options.${property.name}$getter")
        println("set(value) = options.${property.name}.set(value)")
    }
}

private val String.appendWhitespaceIfNotBlank get() = if (isNotBlank()) "$this " else ""

private fun Printer.generateOptionDeprecation(property: KProperty1<*, *>) {
    property.findAnnotation<GradleDeprecatedOption>()
        ?.let { DeprecatedOptionAnnotator.generateOptionAnnotation(it) }
        ?.also { println(it) }
}

private fun Printer.generateDoc(property: KProperty1<*, *>) {
    val description = property.findAnnotation<Argument>()!!.description
    val possibleValues = property.gradleValues.possibleValues
    val defaultValue = property.gradleDefaultValue

    println("/**")
    println(" * $description")
    if (possibleValues != null) {
        println(" * Possible values: ${possibleValues.joinToString()}")
    }
    println(" * Default value: $defaultValue")
    println(" */")
}

private inline fun Printer.withIndent(fn: Printer.() -> Unit) {
    pushIndent()
    fn()
    popIndent()
}

private fun generateMarkdown(properties: List<KProperty1<*, *>>) {
    println("| Name | Description | Possible values |Default value |")
    println("|------|-------------|-----------------|--------------|")
    for (property in properties) {
        val name = property.name
        if (name == "includeRuntime") continue   // This option has no effect in Gradle builds
        val renderName = listOfNotNull("`$name`", property.findAnnotation<GradleDeprecatedOption>()?.let { "__(Deprecated)__" })
            .joinToString(" ")
        val description = property.findAnnotation<Argument>()!!.description
        val possibleValues = property.gradleValues.possibleValues
        val defaultValue = when (property.gradleDefaultValue) {
            "null" -> ""
            "emptyList()" -> "[]"
            else -> property.gradleDefaultValue
        }

        println("| $renderName | $description | ${possibleValues.orEmpty().joinToString()} | $defaultValue |")
    }
}

private val KProperty1<*, *>.gradleValues: DefaultValues
    get() = findAnnotation<GradleOption>()!!.value.objectInstance!!

private val KProperty1<*, *>.gradleDefaultValue: String
    get() = gradleValues.defaultValue

private val KProperty1<*, *>.gradleReturnType: String
    get() {
        // Set nullability based on Gradle default value
        var type = returnType.withNullability(false).toString().substringBeforeLast("!")
        if (gradleDefaultValue == "null") {
            type += "?"
        }
        return type
    }

private val KProperty1<*, *>.gradleLazyReturnType: String
    get() {
        val classifier = returnType.classifier
        return when {
            classifier is KClass<*> && classifier == List::class ->
                "org.gradle.api.provider.ListProperty<${returnType.arguments.first().type!!.withNullability(false)}>"
            classifier is KClass<*> && classifier == Set::class ->
                "org.gradle.api.provider.SetProperty<${returnType.arguments.first().type!!.withNullability(false)}>"
            classifier is KClass<*> && classifier == Map::class ->
                "org.gradle.api.provider.MapProperty<${returnType.arguments[0]}, ${returnType.arguments[1]}"
            else -> "org.gradle.api.provider.Property<${returnType.withNullability(false)}>"
        }
    }

private val KProperty1<*, *>.gradleLazyReturnTypeInstantiator: String
    get() {
        val classifier = returnType.classifier
        return when {
            classifier is KClass<*> && classifier == List::class ->
                ".listProperty(${returnType.arguments.first().type!!.withNullability(false)}::class.java)"
            classifier is KClass<*> && classifier == Set::class ->
                ".setProperty(${returnType.arguments.first().type!!.withNullability(false)}::class.java)"
            classifier is KClass<*> && classifier == Map::class ->
                ".mapProperty(${returnType.arguments[0]}::class.java, ${returnType.arguments[1]}::class.java)"
            else -> ".property(${returnType.withNullability(false)}::class.java)"
        }
    }

private val KProperty1<*, *>.gradleInputType: GradleInputTypes get() = 
    findAnnotation<GradleOption>()!!.gradleInputType

private inline fun <reified T> KAnnotatedElement.findAnnotation(): T? =
    annotations.filterIsInstance<T>().firstOrNull()

object DeprecatedOptionAnnotator {
    fun generateOptionAnnotation(annotation: GradleDeprecatedOption): String {
        val message = annotation.message.takeIf { it.isNotEmpty() }?.let { "message = \"$it\"" }
        val level = "level = DeprecationLevel.${annotation.level.name}"
        val arguments = listOfNotNull(message, level).joinToString()
        return "@Deprecated($arguments)"
    }
}
//endregion
